# test size in megabytes
size=1000
timeout=60
key=disk-read-small

file="/mnt/data/disk-read-small"

if [ ! -f $file ]; then
  date
  echo "Generating test file"
  dd if=/dev/zero of=$file bs=1M count=$size
fi

echo "Drop caches"
free && sync && echo 3 > /proc/sys/vm/drop_caches && free
  
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

echo "$dd_output"

echo $bytes $time $value

curl -i -XPOST 'http://localhost:8086/write?db=db' --data-binary "$key value=$value $t0"

echo $key $t0 $value
