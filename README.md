

# Whiteout Survival Bot - Beta Fork (Personal Optimizations)

**This is my personal beta fork of the original [wos-bot](https://github.com/your-original-wos-bot-link).**

## 🚀 My Personal Changes & Optimizations (Unique to This Fork)

### 🎯 **Task Priority & Coordination**
- **Absolute Intel Priority:** Intel tasks always take precedence over Gathering. Gathering will always wait if Intel is active or scheduled. No more mutual waiting.
- **March Queue Coordination:** No more march slot conflicts between Intel and Gathering tasks.
- **Smart Task Rescheduling:** Improved wait times and conflict resolution between different task types.

### 🛡️ **Enhanced Task Implementations**
- **PetSkillsTask Improvements:** If a skill is "Active", the task is rescheduled for 1 hour instead of looping. Robust cooldown and error handling.
- **Storehouse Chest OCR:** Improved time recognition with support for multiple time formats (HH:mm:ss, mm:ss), better error handling, and only pressing the back button when something is actually claimed.
- **Stamina OCR Fix:** Correctly recognizes stamina values over 1000.
- **Intelligence Task Optimization:** Better march slot management and improved conflict resolution.
- **Gathering Task Enhancement:** Intelligence-aware scheduling with proper wait mechanisms.
- **Tundra Trek Automation (TundraTrekAuto):** Fully automated Tundra Trek run with OCR-driven exit logic and robust timeouts (details below).

### 🎮 **Mercenary Event Support**
- **Upstream Mercenary Event Integration:** Uses the official upstream Mercenary Event implementation from the original repository.
- **Template Resources:** Added comprehensive template image recognition for mercenary-related UI elements.
- **Clean Implementation:** Removed custom mercenary implementations in favor of upstream standards.

### 🛠️ **Development & Deployment Tools**
- **Admin Batch File:** Enhanced batch file for MEmu testing with robust error handling and auto-detection of latest JAR.
- **PowerShell Scripts:** Added `create-admin-exe.ps1` for automated admin execution setup.
- **Project Hygiene:** Improved .gitignore, README, and build scripts for better maintainability.

### 📚 **Documentation & Maintenance**
- **English Documentation:** All docs and logs are now in English for better maintainability.
- **Regular Upstream Sync:** I regularly merge improvements from the original repository to keep this fork up to date.
- **Enhanced UI Labels:** Updated UI controller with cleaner event labeling and proper configuration mapping.

> **This fork is for my own experiments and workflow improvements. It may contain features, workarounds, or changes not present in the official version. Use at your own risk!**

---

## 🧩 Features Inherited from the Original wos-bot

- ✅ Multi-profile support (run multiple accounts simultaneously)
- ✅ **Arena** battles
- ✅ **Polar Terror** hunting
- ✅ **Trains and promotes troops**
- ✅ **Intel**
- ✅ **"My Trucks"** section of the **Tundra Truck Event**
- ✅ **Experts**
- ✅ **Tundra Trek** (random options)
- ✅ **Tundra Trek Supplies**
- ✅ **Journey of Light**
- ✅ **Pet Adventure**
- ✅ **Pet Skills** (Food, Treasure, and Stamina)
- ✅ **Gathers** resources
- ✅ **Daily Shards** from the **War Academy**
- ✅ **Fire Crystals** from the **Crystal Laboratory**
- ✅ **Nomadic Merchant**
- ✅ **Online Rewards**
- ✅ **Hero Recruitment**
- ✅ **Exploration Chests**
- ✅ **Daily VIP Points**
- ✅ **Mail**
- ✅ **Alliance Tech**
- ✅ **Alliance Chests**
- ✅ **Alliance Rallies**

- Multi-profile support (run multiple accounts simultaneously)
- Automates daily **Nomadic Merchant** interactions
- Automatically buys **VIP points** from the merchant
- **Hero Recruitment** automation
- Collects **Daily Shards** from the **War Academy**
- Collects **Fire Crystals** from the **Crystal Laboratory**
- Opens **Exploration Chests**
- Claims **Daily VIP Points**
- Contributes to **Alliance Tech**
- Collects **Alliance Chests**
- Auto **Trains and Promotes Troops**
- Auto activates **Pet Skills** (Food, Treasure and Stamina)
- Claims **Online Rewards**
- Claims **Pet Adventure** chests
- Auto-collect rewards from mail
- **Alliance Auto Join** for rallies
- Automatically **Gathers** resources
- Automate **Intel** completion
- Claims **Tundra Trek Supplies**
- Automates **Tundra Truck Event** "My Trucks" section

---



## 🔧 Recent Improvements & Bugfixes (Latest Updates)

### ✅ **NEW: Tundra Trek Automation (TundraTrekAuto)**
- **What it does:**
  - Navigates to Tundra Trek via the left menu icon only.
  - Presses `Auto` then `Bag` to start automation.
  - Monitors the top-right trek counter (e.g., `14/100`) via OCR.
- **Exit logic:**
  - Pre-check on entry: If `0/100`, exit with a single back and reschedule in 12 hours.
  - During run: Polls OCR every 2.5s.
    - If OCR returns no valid numbers for 2 minutes: exit with double back and reschedule in 10 minutes.
    - If valid numbers are parsed but the value does not decrease for 2 minutes (stays same or increases): exit with single back and reschedule in 10 minutes.
    - If value reaches `0/100`: exit with single back and reschedule in 12 hours.
- **Notes:**
  - Uses calibrated region with multiple offsets and normalization to improve OCR robustness.
  - Start location: `HOME`.
  - UI label updated to "Tundra Trek Automation".
  - Template assets used: `templates/tundratrek/autoTrek.png`, `templates/tundratrek/bagTrek.png`.

### ✅ **NEW: Weekly Triumph Claim Support**
- **Feature:** Added automatic detection and claiming of weekly Alliance Triumph rewards
- **Implementation:**
  - Detects weekly triumph rewards using template image recognition
  - Claims weekly rewards with precise center-point clicking
  - Includes proper escape sequence after claiming
  - Enhanced logging for weekly reward status tracking
- **Status:** ✅ NEW - Weekly Alliance Triumph rewards now fully automated

### ✅ **StorehouseChest: Merge Conflict Resolution & Integration**
- **Problem:** Conflicting logic between master branch (robust OCR) and upstream (stamina scheduling)
- **Solution:** Successfully merged best logic from both branches
  - **From Master:** Robust OCR parsing with 5-attempt retry logic and comprehensive error handling
  - **From Upstream:** Smart stamina claim scheduling based on daily reset timing
  - **Combined Benefits:** Most reliable storehouse logic with intelligent timing
- **Status:** ✅ UPDATED - Combined robust OCR with smart stamina scheduling

### ✅ **Task Coordination & Priority System**
- **Intel-Gathering Priority Fix:** Intel tasks now have absolute priority over Gathering tasks
  - Intel runs immediately regardless of Gathering status
  - Gathering waits 10 minutes when Intel is active/scheduled
  - No more march slot conflicts between task types
- **Status:** ✅ Implemented - Priority-based task execution prevents conflicts

### ✅ **PetSkillsTask: Enhanced Gathering Logic**
- **Problem:** When "Active" status was detected, gathering was attempted too often (endless loops)
- **Solution:**
  - When bot detects "Active", task is rescheduled for 1 hour later (instead of immediate retry)
  - Proper cooldown time rescheduling, 5 minutes on errors
  - Better state detection and handling
- **Status:** ✅ Fixed – Gathering only retries when sensible, no more infinite loops

### ✅ **StorehouseChest: Advanced OCR & Error Handling**
- **Problem:** Remaining time was often not detected or incorrectly parsed, causing loops
- **Solution:**
  - **Multi-format Time Parsing:** Supports HH:mm:ss, mm:ss, and malformed OCR outputs
  - **Robust Preprocessing:** Removes spaces, corrects common OCR errors (O→0, I→1, S→5)
  - **Smart Error Handling:** Automatic rescheduling on OCR failures
  - **Improved Logging:** Uses proper logging framework instead of System.err
  - **Back Button Logic:** Only pressed when rewards are actually claimed
- **Status:** ✅ Fixed - Significantly more stable OCR detection and processing

### ✅ **Stamina OCR Detection Fixed**
- **Problem:** Bot only detected first digit for stamina values over 1000 (e.g., "1" instead of "1454")
- **Solution:** Enhanced OCR text cleaning and extended detection region
- **Status:** ✅ Fixed - Stamina values above 1000 are now correctly detected

### ✅ **Mercenary Event Integration**
- **Implementation:** Integrated upstream Mercenary Event support from original repository
- **Features:**
  - Uses official upstream MercenaryEventTask implementation
  - Added comprehensive template image resources (Merc.png, Merc1.png, Merc2.png, etc.)
  - Clean UI integration with proper configuration mapping
- **Status:** ✅ Implemented - Uses stable upstream implementation

### ✅ **Development & Build Improvements**
- **Admin Execution:** Added `run-as-admin.bat` for MEmu compatibility
- **PowerShell Scripts:** Created `create-admin-exe.ps1` for automated setup
- **Enhanced .gitignore:** Better exclusion patterns for development files
- **Improved Build Process:** Updated Maven configuration and dependency management

---

## 📊 **Complete Differences from Upstream Repository**

This fork contains **540 insertions and 105 deletions** across **22 files** compared to the upstream [camoloqlo/wosbot](https://github.com/camoloqlo/wosbot) repository.

### 🔧 **Modified Core Task Files:**
- **`StorehouseChest.java`** - UPDATED: Merged robust OCR parsing with smart stamina scheduling, comprehensive error handling
- **`TriumphTask.java`** - NEW: Added weekly triumph detection and claiming with proper escape sequences
- **`PetSkillsTask.java`** - Better "Active" state handling, reduced infinite loops, smarter rescheduling
- **`IntelligenceTask.java`** - Priority system implementation, march slot coordination
- **`GatherTask.java`** - Intelligence-aware scheduling, conflict avoidance mechanisms
- **`DelayedTaskRegistry.java`** - Updated task registration for new implementations

### 🎯 **Added Features:**
- **`MercenaryPrestigeTask.java`** - Custom mercenary prestige automation (136 lines)
- **6 Mercenary Template Images** - Comprehensive UI recognition resources
- **`run-as-admin.bat`** - MEmu compatibility script (77 lines)
- **`create-admin-exe.ps1`** - PowerShell automation script (87 lines)
- **`TundraTrekAutoTask.java`** - Automated Tundra Trek with OCR-based exit and smart rescheduling

### 🖼️ **UI & Configuration Updates:**
- **`EventsLayoutController.java`** - Enhanced event UI management
- **`LauncherLayoutController.java`** - Updated launcher interface
- **`PetsLayoutController.java`** - Improved pets management UI
- **`FXApp.java`** - Application initialization improvements
- **`EnumTemplates.java`** - Added mercenary template definitions
- **`CityEventsLayout.fxml` / `TpDailyTaskEnum.java`** - Label updated to "Tundra Trek Automation" and config mapping aligned

### 📁 **Project Infrastructure:**
- **`.gitignore`** - Enhanced exclusion patterns (18 additions)
- **`README.md`** - Comprehensive documentation (157 modifications)
- **`pom.xml`** - Build configuration updates

### 🔄 **Upstream Compatibility:**
- **Regular Sync:** This fork regularly merges upstream improvements
- **Selective Integration:** Cherry-picks beneficial changes while maintaining custom optimizations
- **Standard Compliance:** Uses upstream Mercenary Event implementation instead of custom variants

### 📈 **Statistics (Updated September 2025):**
```
Files Changed: 24
Lines Added: +580
Lines Removed: -120
Net Change: +460 lines
Binary Files: 7 (template images including triumphWeekly.png)
Custom Features: 6 major task improvements
Recent Additions: Weekly Triumph automation, StorehouseChest merge optimization
Infrastructure: 3 build/deployment enhancements
```

> **Note:** All changes are designed to be compatible with the upstream repository structure while adding substantial improvements to task reliability, OCR accuracy, and user experience.

---

## ⚙️ Configuration

The bot is configured for **MEmu Android Emulator** with the following settings:

- **Resolution:** 720x1280 (320 DPI)  
- **CPU:** 2 cores  
- **RAM:** 2GB 
- **Language:** English

---

## 🛠️ Building & Running

### 1️⃣ Install Requirements

* **Java (JDK 17 or newer)**
  👉 Download from [Adoptium Temurin](https://adoptium.net/)

* **Apache Maven** (for building the project)
  👉 Download from [Maven official site](https://maven.apache.org/install.html)

### 2️⃣ Add to PATH (Windows Users)

After installing, you need to add **Java** and **Maven** to your environment variables:

1. Press **Win + R**, type `sysdm.cpl`, and press **Enter**.
2. Go to **Advanced → Environment Variables**.
3. Under **System variables**, find `Path`, select it, and click **Edit**.
4. Add the following entries (adjust if installed in a different folder):

   ```
   C:\Program Files\Eclipse Adoptium\jdk-17\bin
   C:\apache-maven-3.9.9\bin
   ```
5. Click **OK** and restart your terminal (or reboot if needed).

✅ Verify installation:

```sh
java -version
mvn -version
```

### 3️⃣ Compile the Project

In the project’s root folder, run:

```sh
mvn clean install package
```

This will generate a `.jar` file inside the **`wos-hmi/target`** directory.
Example:

```
wos-hmi/target/wos-bot-1.5.4.jar
```

### 4️⃣ Run the Bot

#### ✅ Recommended: Run from Command Line

This way you can see real-time logs (useful for debugging).

```sh
# Navigate to the target directory
cd wos-hmi/target

# Run the bot (replace X.X.X with the version you built)
java -jar wos-bot-X.X.X.jar
```

#### Via Admin Batch File
For MEmu testing, use the included admin batch file to start with proper permissions:
```sh
# Run as administrator for MEmu compatibility
run-as-admin.bat
```

#### Double-click
The bot can also be started by double-clicking the `wos-bot-x.x.x.jar` file. No console will be shown for logs.

### 5️⃣ Emulator setup — choose the correct executable

Supported emulators: MuMu Player, MEmu, LDPlayer 9.

When the launcher asks you to choose your emulator executable, select the command-line controller for your emulator (not the graphical player app). Below are the executables you should select for each supported emulator, with typical default paths on Windows:

- MuMu Player
  - Executable: MuMuManager.exe
  - Default path: `C:\Program Files\Netease\MuMuPlayerGlobal-12.0\shell\`
                  `C:\Program File\Netease\MuMuPlayer\nx_main\`
- MEmu
  - Executable: memuc.exe
  - Default path: `C:\Program Files\\Microvirt\MEmu\`

- LDPlayer 9
  - Executable: ldconsole.exe
  - Default path: `C:\LDPlayer\LDPlayer9\`

Notes:
- If your emulator is installed in a different location, browse to the folder where that executable resides and select it.
- These executables provide command-line control so the bot can launch/close instances and detect whether they are running.
- LDPlayer only: You must manually enable ADB in the instance settings (Settings → Other settings → ADB debugging = Enable local connection), otherwise the bot cannot connect via ADB.

#### Instance settings

The bot is designed to run on MuMu Player with the following settings:
- Resolution: 720x1280 (320 DPI) (mandatory)
- CPU: 2 Cores
- RAM: 2 GB
- Game Language: English (mandatory)

Note: For best performance and reliability, disable the Snowfall and Day/Night Cycle options in the in-game settings, and avoid using Ultra graphics quality.

---

### 🚀 Future Features (Planned)
- 🔹 **Beast Hunt**
- 🔹 **Alliance Mobilization**
- 🔹 **Fishing Event**
- 🔹 **And more...** 🔥

---

