import sys
import subprocess
import time
import os

# --- Port configuration for simulators ---
DEVICE_PORTS = [
    9091,
    9092,
    9093,
    9094,
    9095,
    9096,
    9097,
    9098,
    9099,
    9100,
]

# Dictionary for storing running processes: {port: <process object>}
running_processes = {}
# Dictionary for storing open log files: {port: <file handle>}
log_files = {}

def clear_screen():
    """Cleans the terminal screen"""
    os.system('cls' if os.name == 'nt' else 'clear')

def check_running_processes():
    """Checks which processes are still alive and removes dead ones"""
    global running_processes
    to_remove = []
    for port, proc in running_processes.items():
        if proc.poll() is not None:
            print(f"--- [Manager] Device on port {port} (PID: {proc.pid}) has stopped. ---")
            print(f"--- [Manager] Log file: {port}.log ---")
            to_remove.append(port)
            log_files[port].close()

    for port in to_remove:
        del running_processes[port]
        del log_files[port]

def display_menu():
    """Shows the main menu"""
    clear_screen()
    print("====================================")
    print("  Smart IoT Device Manager")
    print("====================================")

    check_running_processes()

    print("\n--- 🟢 Active Devices (Running) ---")
    if not running_processes:
        print("   (No active devices)")
    else:
        for port, proc in running_processes.items():
            print(f"   - Port: {port} (PID: {proc.pid}, Log: {port}.log)")

    print("\n--- 😴 Sleeping Devices (Slots) ---")
    stopped_ports = [port for port in DEVICE_PORTS if port not in running_processes]
    if not stopped_ports:
        print("   (All slots are in use)")
    else:
        for port in stopped_ports:
            print(f"   - Slot on port: {port}")

    print("\n------------------------------------")
    print("  (s) Start Device (by port)")
    print("  (k) Kill Device (by port)")
    print("  (l) View Log")
    print("  (r) Refresh")
    print("  (q) Quit")
    print("------------------------------------")
    return input("Select action: ").lower()

def start_devices():
    """Starts the selected device as a new process"""
    user_input = input("Enter port(s) to start (comma separated, e.g., 9091, 9092): ")
    # 1. Input parsen
    try:
        # Splitte String am Komma und konvertiere zu Integers
        ports_to_start = [int(p.strip()) for p in user_input.split(',') if p.strip()]
    except ValueError:
        print("--- Error: Invalid input. Please enter numbers separated by commas. ---")
        time.sleep(2)
        return

    if not ports_to_start:
        return

    print(f"\n--- Attempting to start: {ports_to_start} ---")

    for port in ports_to_start:
        if port not in DEVICE_PORTS:
            print(f"⚠️  Skipping {port}: Not in configuration.")
            continue
        if port in running_processes:
            print(f"⚠️  Skipping {port}: Already running.")
            continue

        command = [sys.executable, "-u", "device_simulator.py", str(port)]
        try:
            env = os.environ.copy()
            env["PYTHONIOENCODING"] = "utf-8"

            log_file = open(f"{port}.log", "w", encoding="utf-8")
            proc = subprocess.Popen(command, stdout=log_file, stderr=log_file, env=env)

            running_processes[port] = proc
            log_files[port] = log_file
            print(f"✅ Started device on port {port} (PID: {proc.pid})")

            # Kleine Pause, um CPU-Spikes beim Starten vieler Prozesse zu vermeiden
            time.sleep(0.5)

        except Exception as e:
            print(f"❌ Failed to start {port}: {e}")

    input("\n--- Press Enter to return to menu ---")

def kill_device():
    """Forcibly stops the device process"""
    try:
        port = int(input("Enter port of active device to kill: "))
    except ValueError:
        print("--- Error: Please enter a number. ---")
        time.sleep(2)
        return

    if port not in running_processes:
        print(f"--- Error: Device on port {port} is not running ---")
        time.sleep(2)
        return

    proc = running_processes.pop(port)
    log_file = log_files.pop(port)

    proc.terminate()
    log_file.close()
    print(f"--- Sent termination signal to port {port} (PID: {proc.pid}) ---")
    time.sleep(2)

def view_log():
    """Shows the last 20 lines of the device log"""
    try:
        port = int(input("Enter device port to view log: "))
    except ValueError:
        print("--- Error: Please enter a number. ---")
        time.sleep(2)
        return

    log_filename = f"{port}.log"
    if not os.path.exists(log_filename):
        print(f"--- Error: Log file {log_filename} not found. ---")
        time.sleep(2)
        return

    clear_screen()
    print(f"--- 📜 Last 20 lines of {log_filename} (Press Enter to return) ---")
    try:
        with open(log_filename, 'r', encoding='utf-8') as f:
            lines = f.readlines()
            for line in lines[-20:]:
                print(line.strip())
    except Exception as e:
        print(f"--- Error reading log: {e} ---")

    input("\n--- Press Enter to return to menu ---")

def shutdown_all():
    """Stops all child processes upon exit"""
    print("--- Stopping all active devices... ---")
    for port, proc in running_processes.items():
        print(f"Stopping {port} (PID: {proc.pid})...")
        proc.terminate()
    for port, f in log_files.items():
        f.close()
    print("--- Shutdown complete. Exiting. ---")


# --- Main cycle of the Manager ---
if __name__ == "__main__":
    try:
        while True:
            action = display_menu()

            if action == 's':
                start_devices()
            elif action == 'k':
                kill_device()
            elif action == 'l':
                view_log()
            elif action == 'r':
                continue
            elif action == 'q':
                shutdown_all()
                break
            else:
                print(f"--- Unknown command '{action}' ---")
                time.sleep(1)
    except KeyboardInterrupt:
        print("\n🛑 Manager stopped by user.")
        shutdown_all()