# test size in megabytes
size=4000
key=disk-write

date
echo "Measuring $key"

t0=$(date +%s%N)
dd_output=$(dd if=/dev/zero of=zeros bs=1M count=$size 2>&1)
sync
t1=$(date +%s%N)

echo "$dd_output"

time=$(echo "($t1 - $t0) / 10^9" | bc -l)
bytes=$(echo "$size * 10^6" | bc -l)
value=$(echo "$bytes / $time" | bc -l)

echo $key $t0 $bytes bytes, $time seconds, $value B/s

curl -i -XPOST 'http://localhost:8086/write?db=db' --data-binary "$key value=$value $t0"
