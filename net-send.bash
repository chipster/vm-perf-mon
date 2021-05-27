key=net-send

date
echo "Measuring $key"

t0=$(date +%s%N)

#value=$(iperf -c iperf.funet.fi --reportstyle c | cut -d "," -f 9)
iperf_output=$(timeout 3 iperf3 -c iperf.funet.fi --time 1 --json)

echo "$iperf_output"

#value=$(echo "$iperf_output" | grep bits_per_second | tail -n 1 | cut -d ":" -f 2 | sed 's/	//g' | sed 's/ //g' | sed 's/e+/\*10\^/' | bc)
value=$(echo "$iperf_output" | jq .end.sum_received.bits_per_second)

echo "$value b/s"

value=$(echo "$value / 8" | bc)

echo "$value B/s"

echo $key $t0 $value B/s

curl -i -XPOST 'http://localhost:8086/write?db=db' --data-binary "$key value=$value $t0"
