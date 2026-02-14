# MyBattery

<img src="https://github.com/user-attachments/assets/7ead41a7-9d7b-4ce0-bf57-b9ef57ed6205" width="222">
<img src="https://github.com/user-attachments/assets/20247c8d-9865-4778-afd1-63840fc8055d" width="222">

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
  The raw value is stored as cycles × 1000.
  Example: `157000` means about 157 cycles.

- `LLB CAL` or `LLB MAN`
  Battery manufacturing / assembly date.
  Used to estimate when the battery was produced.

- Log timestamp (from dumpstate header or filename)
  The app detects when the log was created so it can show how recent the data is.



> [!IMPORTANT]
> MyBattery is not an official Samsung app.  
> Battery results depend on the log files available on the device.
