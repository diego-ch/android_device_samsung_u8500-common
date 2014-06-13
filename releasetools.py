import common

def FullOTA_InstallEnd(info):
	info.script.AppendExtra('symlink("/system/lib/libjhead.so", "/system/lib/libhead.so");')
