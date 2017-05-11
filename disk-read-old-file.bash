# test size in megabytes
size=4000
timeout=1800
file_count=70
key=disk-read-old-file

if [ ! -f /mnt/data/zeros_0 ]; then
  date
  for i in $(seq 0 $file_count); do
  	echo "Generating test file $i"
    dd if=/dev/zero of=/mnt/data/zeros_$i bs=1M count=$size
  done
  echo "Test files ready"
  ls -lah $file
fi

# try to avoid the Ceph caches by reading from the least recently accessed file
file=$(ls -u -t /mnt/data/zeros_* | tail -n 1)

date
echo "Measuring $key"

t0=$(date +%s%N) 
timeout $timeout dd if=$file of=/dev/null bs=1M count=$size 2>&1
exit_val=$?

if [ $exit_val -eq 0 ] ; then 
  t1=$(date +%s%N)
  
  time=$(echo "($t1 - $t0) / 10^9" | bc -l)
  bytes=$(echo "$size * 10^6" | bc -l)
  value=$(echo "$bytes / $time" | bc -l)
else  
  echo "Timeout"
  value=0
fi

echo $bytes $time $value

curl -i -XPOST 'http://localhost:8086/write?db=db' --data-binary "$key value=$value $t0"

echo $key $t0 $value
