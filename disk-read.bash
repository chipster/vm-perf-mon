# test size in megabytes
size=4000
file_count=15
key=disk-read

# try to avoid the OSD page cache by selecting a file to read by random
file_num=$(shuf -i 0-$file_count -n 1)
file=$(echo rand_$file_num)

if [ ! -f $file ]; then
  date
  echo "Generating test file"
  rm $file
  # create 100 MB of random bytes
  dd if=/dev/urandom of=rand bs=1M count=100
  # repeat the file until we have enough test data
  chunk_count=$(echo "$size / 100" | bc)
  for i in $(seq 0 $chunk_count); do
    dd if=rand >> $file
    # don't make this too easy for the KSM by shifting the random content
    dd if=/dev/urandom bs=$(shuf -i 0-4096 -n 1) count=1 >> $file
  done
  date
  echo "Test file ready"
  ls -lah $file
fi

date
echo "Measuring $key"

t0=$(date +%s%N)
dd_output=$(dd if=$file of=/dev/null bs=1M count=$size 2>&1)
sync
t1=$(date +%s%N)

echo "$dd_output"

time=$(echo "($t1 - $t0) / 10^9" | bc -l)
bytes=$(echo "$size * 10^6" | bc -l)
value=$(echo "$bytes / $time" | bc -l)

echo $bytes $time $value

curl -i -XPOST 'http://localhost:8086/write?db=db' --data-binary "$key value=$value $t0"

echo $key $t0 $value
