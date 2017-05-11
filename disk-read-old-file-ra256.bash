# test size in megabytes
size=1
# timeout fast to limit distraction for other measurements
timeout=30
key=disk-read-old-file-ra256
readahead=256

if [ ! -f /mnt/data/zeros_0 ]; then
  echo "Test files not found"
  exit 1
fi

# try to avoid the Ceph caches by reading from the least recently accessed file
file=$(ls -u -t /mnt/data/zeros_* | tail -n 1)


date
echo "Measuring $key"

original_readahead=$(blockdev --getra /dev/vdb)
echo "Setting readahead to $readahead"
blockdev --setra $readahead /dev/vdb
blockdev --getra /dev/vdb

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

echo "Restore readahead to $original_readahead"
blockdev --setra $original_readahead /dev/vdb
blockdev --getra /dev/vdb

echo $bytes $time $value

curl -i -XPOST 'http://localhost:8086/write?db=db' --data-binary "$key value=$value $t0"

echo $key $t0 $value
