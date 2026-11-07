# MyBattery

<img width="250" alt="Screenshot_20261107-073946_My Battery" src="https://github.com/user-attachments/assets/a595121e-b44f-4a02-9077-caf02e98947c" />
<img width="250" alt="Screenshot_20261107-073941_My Battery" src="https://github.com/user-attachments/assets/4292ab59-fc88-4df1-a77a-277c6ec89eac" />



## Nerdy details
MyBattery reads Samsung dumpstate battery logs (`dumpState_*.log`) and extracts the following values:

- `mSavedBatteryAsoc`
  Battery health percentage (main value).
  This is the most accurate capacity estimate Samsung provides.
  Example: `96` means the battery holds about 96% of its original capacity.

- `mSavedBatteryBsoh`
  Battery health percentage (fallback value).
  Used only if `mSavedBatteryAsoc` is missing or unsupported.

- `mSavedBatteryUsage`
  Battery cycle count (number of charge cycles).
  The raw value is stored as cycles × 100.
  Example: `157000` means about 1570 cycles.

- `LLB CAL` or `LLB MAN`
  Battery manufacturing / assembly date.
  Used to estimate when the battery was produced.

- Log timestamp (from dumpstate header or filename)
  The app detects when the log was created so it can show how recent the data is.



> [!IMPORTANT]
> MyBattery is not an official Samsung app.  
> Battery results depend on the log files available on the device.
