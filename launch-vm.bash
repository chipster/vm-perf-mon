#!/bin/bash

set -e

if [ -z $2 ]; then
  echo "Usage bash launch-vm.bash INFLUXDB_ADDRESS IMAGE"
  exit 1
fi

influxdb_ip="$1"
image="$2"
flavor="standard.tiny"
instance_name="vm-launch-test-$image"

timeout=1200
key=vm-launch

date
whoami

function finish {
  echo "delete instance $instance_name (in exit trap)"
  openstack server delete $instance_name || true
  echo "done"
}
trap finish EXIT

echo "delete possible old instance $instance_name"
openstack server delete $instance_name || true

date
echo "Measuring $key"

t0=$(date +%s%N)
echo "launch $image $flavor"
launch_output="$(timeout $timeout openstack server create --image $image --flavor $flavor --security-group default --key-name jenkins_id_rsa --wait $instance_name -f json)"
launch_exit_value=$?
echo "launch exit value: $launch_exit_value"

t1=$(date +%s%N)

echo "server create output: "
#echo "$launch_output"

vm_ip=$(echo "$launch_output" | jq .addresses -r | cut -d "=" -f 2)

echo "VM IP address: $vm_ip"

ssh-keygen -f "/home/ubuntu/.ssh/known_hosts" -R $vm_ip

echo "wait VM to boot"
retries=60
for i in $(seq 1 $retries); do
  if [ $i == $retries ]; then
    echo "error: timeout after $retries tries"
    exit 1
  fi

  if timeout 1 ssh -i /home/ubuntu/.ssh/id_rsa_jenkins ubuntu@$vm_ip hostname; then
    ssh_exit_value=$?
    echo "ssh exit value: $ssh_exit_value"
    break
  fi
  echo -n "."
  sleep 1
done

if [ $launch_exit_value -eq 0 ] && [ $ssh_exit_value -eq 0 ]; then 
  t2=$(date +%s%N)
  
  launch_time=$(echo "($t1 - $t0) / 10^9" | bc -l)
  boot_time=$(echo "($t2 - $t1) / 10^9" | bc -l)
  total_time=$(echo "($t2 - $t0) / 10^9" | bc -l)
else  
  echo "error exit value"
  launch_time=0
  boot_time=0
  total_time=0
fi

echo "results: "
echo $key,launch $t0 $launch_time seconds
echo $key,boot $t0 $boot_time seconds
echo $key,total $t0 $total_time seconds

influxdb_url="http://localhost:8086/write?db=db"

echo "send results to InfluxDB"
ssh -i /home/ubuntu/.ssh/id_rsa_jenkins $influxdb_ip curl -s -XPOST $influxdb_url --data-binary \"$key,step=launch,image=$image value=$launch_time $t0\"
ssh -i /home/ubuntu/.ssh/id_rsa_jenkins $influxdb_ip curl -s -XPOST $influxdb_url --data-binary \"$key,step=boot,image=$image value=$boot_time $t0\"
ssh -i /home/ubuntu/.ssh/id_rsa_jenkins $influxdb_ip curl -s -XPOST $influxdb_url --data-binary \"$key,step=total,image=$image value=$total_time $t0\"
