# test size in megabytes
size=4000
timeout=60
key=disk-read-same-file

file="/mnt/data/disk-read-new"

if [ ! -f $file ]; then
  date
  echo "Generating test file"
  dd if=/dev/zero of=$file bs=1M count=$size
fi
  
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
