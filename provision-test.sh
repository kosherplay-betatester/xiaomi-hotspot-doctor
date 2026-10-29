#!/system/bin/sh
# Test provisioning script for Shizuku Shell "Run script from URL".
# Harmless/read-only - safe to run. Edit this on your server to change what gets provisioned.
echo "== provision script v1 =="
echo "running as uid: $(id -u) ($(id -un 2>/dev/null))"
echo "device: $(getprop ro.product.model) / Android $(getprop ro.build.version.release)"
echo "target package field (PKG) = PKG"
echo "hotspot: $(dumpsys wifi 2>/dev/null | grep -m1 mCurrentSoftApInfoMap | grep -o 'frequency= [0-9]*')"
echo "== end of script =="
