export LC_ALL=en_US.UTF-8
export LANG=en_US.UTF-8

scp * vm0099.kaj.pouta.csc.fi:petri/vm-perf-mon/ ; ssh vm0099.kaj.pouta.csc.fi "source chipcld-openrc.sh ; cd petri/vm-perf-mon ; ansible-playbook demo.yml -e floating_ip=86.50.168.179"
