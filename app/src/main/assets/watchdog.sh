# Pengaman suhu yang berjalan di level root, TERPISAH dari aplikasi.
# Jika suhu lewat batas (atau aplikasi mati), pengaturan asli dipulihkan.
BL=$1
CL=$2
rd() { v=; { read -r v < "$1"; } 2>/dev/null; }
while [ -e /dev/gb_active ]; do
  rd /sys/class/power_supply/battery/temp; b=${v:-0}
  m=0
  for z in /sys/class/thermal/thermal_zone*; do
    rd "$z/type"; ty=$v
    case "$ty" in
      *cpu*|*soc*|*tsens*|*cluster*)
        rd "$z/temp"; t=${v:-0}
        if [ "$t" -gt "$m" ] 2>/dev/null && [ "$t" -lt 125000 ] 2>/dev/null; then m=$t; fi;;
    esac
  done
  if [ "$b" -ge $((BL * 10)) ] 2>/dev/null || [ "$m" -ge $((CL * 1000)) ] 2>/dev/null; then
    sh /dev/gb_restore.sh
    rm -f /dev/gb_active
    break
  fi
  sleep 3
done
