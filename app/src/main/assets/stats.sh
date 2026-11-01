rd() { v=; { read -r v < "$1"; } 2>/dev/null; }

rd /sys/class/power_supply/battery/temp; bt=$v
rd /sys/class/power_supply/battery/capacity; bc=$v
echo "BATT|$bt|$bc"

for c in /sys/devices/system/cpu/cpu[0-9]*; do
  rd $c/cpufreq/scaling_cur_freq; f=$v
  rd $c/cpufreq/scaling_governor; g=$v
  echo "CPU|${c##*/}|$f|$g"
done

for z in /sys/class/thermal/thermal_zone*; do
  rd $z/type; ty=$v
  rd $z/temp; tp=$v
  echo "TZ|$ty|$tp"
done

rd /sys/class/kgsl/kgsl-3d0/gpuclk; gc=$v
rd /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage; gb=$v
echo "KGSL|$gc|$gb"

for f in /sys/class/devfreq/*/cur_freq; do
  case "$f" in
    *gpu*|*mali*|*g3d*) rd $f; echo "DEVF|$v"; break;;
  esac
done

echo "MEM|$(grep -E 'MemTotal|MemAvailable' /proc/meminfo | tr '\n' ' ')"
read -r st < /proc/stat
echo "STAT|$st"
