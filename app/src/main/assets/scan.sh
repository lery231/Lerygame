# Variabel CPU, MINF, GPU, THERMAL (0/1) diisi oleh aplikasi sebelum skrip ini.
# Output: F|path|nilai_asli|nilai_boost  dan  S|service|status
emit() { if [ -e "$1" ]; then echo "F|$1|$(cat "$1" 2>/dev/null)|$2"; fi; }

if [ "$CPU" = 1 ]; then
  for f in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_governor; do
    emit "$f" performance
  done
fi

if [ "$MINF" = 1 ]; then
  for d in /sys/devices/system/cpu/cpu[0-9]*/cpufreq; do
    m=$(cat "$d/cpuinfo_max_freq" 2>/dev/null)
    if [ -n "$m" ]; then
      emit "$d/scaling_max_freq" "$m"
      emit "$d/scaling_min_freq" "$m"
    fi
  done
fi

if [ "$GPU" = 1 ]; then
  for f in /sys/class/kgsl/kgsl-3d0/devfreq/governor /sys/class/devfreq/*/governor; do
    case "$f" in
      *kgsl*|*gpu*|*mali*|*g3d*) emit "$f" performance;;
    esac
  done
  # Adreno (Snapdragon 660 / Redmi Note 7): lepas batas level daya GPU akibat thermal
  emit /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel 0
fi

if [ "$THERMAL" = 1 ]; then
  for z in /sys/class/thermal/thermal_zone*; do
    t=$(cat "$z/type" 2>/dev/null)
    # zona baterai/charger/USB TIDAK disentuh demi keamanan
    case "$t" in
      *batt*|*bms*|*chg*|*charg*|*usb*|*quiet*) continue;;
    esac
    emit "$z/mode" disabled
  done
  emit /sys/module/msm_thermal/parameters/enabled N
  emit /sys/kernel/msm_thermal/enabled 0
  for s in thermal-engine vendor.thermal-engine thermald mi_thermald thermal_manager; do
    echo "S|$s|$(getprop init.svc.$s)"
  done
fi
