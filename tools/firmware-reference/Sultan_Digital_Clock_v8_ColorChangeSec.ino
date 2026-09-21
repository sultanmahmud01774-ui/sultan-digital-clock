/*
  SULTAN DIGITAL CLOCK — FIXED & IMPROVED VERSION
  ESP8266 + DS3231 + WS2812B + LDR

  ✅ ALL FEATURES:
  - 4-digit HH:MM 7-segment LED display (30 LEDs)
  - DS3231 RTC with accurate timekeeping
  - 2 Alarms with buzzer
  - Web UI: Complete control
  - Phone time sync + Auto NTP sync (UTC+6 Bangladesh Fixed)
  - Color modes: Static / Smooth Fade / Rainbow / Custom RGB / Sweep Random 
  - 12/24 hour format
  - Smart Date Display (English/Bangla)
  - Brightness: Manual + LDR Auto
  - WiFi Auto Reconnect
  - D7 Light Control
  - Display ON/OFF Manual & Auto Schedule
  - All settings saved to EEPROM


  Developer: MD: SULTAN MAHAMUD
  Mobile: 01740-236384
  Email: sultanmahamud5497@gmail.com
  Fixed & Updated: 2026-09-17 (v6 — Color Playlist HTML fix + safer auto-refresh)

  v6 AUDIT NOTES (what changed vs the v5 file you uploaded):
  - CRITICAL FIX: Smart Color Playlist step cards had an unterminated HTML
    attribute quote (id='stepmeta.../id='stepdur...). This silently broke the
    markup of every scene card the first time the page loaded (before any
    dropdown was touched), garbling the "SCENE" title row, duration badge and
    everything rendered after it. Fixed by properly closing both quotes.
  - UX FIX: the whole Web UI auto-refreshed every 60s unconditionally, which
    could wipe unsaved edits while you were mid-way through building a
    playlist, typing a WiFi password, or dragging an RGB slider. It now skips
    the refresh while a field is focused or has been edited since the last
    check.
  - POLISH: renamed one built-in ringtone that had a profane internal name to
    "Retro Groove" (the melody itself is unchanged).

  Footer: ALHAMDULILLAH
*/

#include <Wire.h>
#include "RTClib.h"
#include <Adafruit_NeoPixel.h>
#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <EEPROM.h>
#include <WiFiUdp.h>
#include <NTPClient.h>
#include <ESP8266HTTPUpdateServer.h>  // OTA Web Update

// ========== PROGMEM STRINGS ==========
const char STR_STARTUP[]             PROGMEM = "\n\n=== SULTAN DIGITAL CLOCK (FIXED VERSION) ===";
const char STR_RTC_INIT[]            PROGMEM = "RTC initialized";
const char STR_RTC_NOTFOUND[]        PROGMEM = "RTC not found!";
const char STR_RTC_LOSTPOWER[]       PROGMEM = "RTC lost power, setting compile time...";
const char STR_SETTINGS_LOADED[]     PROGMEM = "Settings loaded from EEPROM";
const char STR_SETTINGS_SAVED[]      PROGMEM = "Settings saved to EEPROM";
const char STR_SETUP_COMPLETE[]      PROGMEM = "Setup complete!";
const char STR_WIFI_CONNECTING[]     PROGMEM = "Connecting to WiFi: ";
const char STR_WIFI_CONNECTED[]      PROGMEM = "WiFi Connected!";
const char STR_WIFI_FAILED[]         PROGMEM = "WiFi Connection Failed!";
const char STR_WIFI_DISCONNECTED[]   PROGMEM = "WiFi disconnected!";
const char STR_WIFI_RECONNECTING[]   PROGMEM = "Attempting to reconnect WiFi...";
const char STR_WIFI_RECONNECTED[]    PROGMEM = "WiFi Reconnected!";
const char STR_WIFI_RECONNECT_FAILED[] PROGMEM = "WiFi Reconnection Failed!";
const char STR_IP_ADDRESS[]          PROGMEM = "IP Address: ";
const char STR_NTP_SYNCING[]         PROGMEM = "Auto syncing time from NTP...";
const char STR_NTP_REQUESTING[]      PROGMEM = "Requesting NTP time...";
const char STR_NTP_INVALID[]         PROGMEM = "Invalid NTP time: ";
const char STR_NTP_OUTOFRANGE[]      PROGMEM = "NTP time out of expected range: ";
const char STR_NTP_HOURLY[]          PROGMEM = "Hourly auto NTP sync completed";
const char STR_RTC_NOTAVAIL[]        PROGMEM = "RTC not available";
const char STR_RTC_UPDATE_FAILED[]   PROGMEM = "RTC update failed";
const char STR_CURRENT_TIME[]        PROGMEM = "Current time: ";
const char STR_NEW_TIME[]            PROGMEM = "New time: ";
const char STR_ALARM_TRIGGER[]       PROGMEM = "Alarm triggered!";
const char STR_ALARM_STOP[]          PROGMEM = "Alarm stopped";
const char STR_WEBSERVER_STARTED[]   PROGMEM = "Web server started";
const char STR_WIFI_SETTINGS_SAVED[] PROGMEM = "WiFi settings saved. Restarting...";
const char STR_TIME_SYNCED_PHONE[]   PROGMEM = "Time synced from phone";
const char STR_NTP_SYNC_SUCCESS[]    PROGMEM = "NTP sync successful! (UTC+6 Bangladesh)";
const char STR_NTP_FAILED[]          PROGMEM = "NTP sync failed";
const char STR_D7_LIGHT[]            PROGMEM = "D7 Light: ";
const char STR_WIFI_NOT_CONNECTED[]  PROGMEM = "WiFi not connected";
const char STR_AP_MODE_SSID[]        PROGMEM = "AP Mode SSID: ";
const char STR_AP_PASSWORD[]         PROGMEM = "AP Password: ";
const char STR_AP_IP[]               PROGMEM = "AP IP Address: ";
const char STR_CONNECTED_TO[]        PROGMEM = "Connected to WiFi: ";
const char STR_CLIENT_IP[]           PROGMEM = "Client IP: ";

// ========== HARDWARE CONFIG ==========
#define LED_PIN    D6
// FIX (user confirmed actual wiring): হার্ডওয়্যারে আসলে ৩০টা LED আছে —
// প্রথম ১৪টা LED = ঘন্টা (২ digit x ৭ segment), তারপর ২টা LED = colon,
// তারপর পরের ১৪টা LED = মিনিট (২ digit x ৭ segment)। সেকেন্ড বা দ্বিতীয়
// colon-এর জন্য কোনো LED নেই — তাই ঘড়ি সবসময় শুধু HH:MM দেখাবে।
#define NUM_LEDS   30
#define BUZZER_PIN D5
#define LDR_PIN    A0
#define LIGHT_PIN  D7

// ========== NTP VALIDATION CONSTANTS ==========
#define NTP_MIN_VALID_TIME    1000000000UL
#define NTP_MIN_EXPECTED_TIME 1735689600UL
#define NTP_MAX_EXPECTED_TIME 2208988800UL
#define BANGLADESH_OFFSET     21600UL       // UTC+6 = 6*3600

// ========== RTC ==========
RTC_DS3231 rtc;
bool rtcOK = false;

// ========== NTP CLIENT (offset=0, raw UTC only) ==========
WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "pool.ntp.org", 0, 60000);

// ========== LED SETUP ==========
Adafruit_NeoPixel strip(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);

const byte digitsMatrix[] = {
  0b1111110, 0b0011000, 0b0110111, 0b0111101, 0b1011001,
  0b1101101, 0b1101111, 0b0111000, 0b1111111, 0b1111101,
  0b0000000
};

// FIX: ৪টা digit — hourTens(0-6), hourOnes(7-13), minuteTens(16-22), minuteOnes(23-29)
// মাঝখানে ১টা colon (14,15)। সেকেন্ড digit বা দ্বিতীয় colon হার্ডওয়্যারে নেই।
int digitOffset[4] = {0, 7, 16, 23};
int colon1[2] = {14, 15};

// ========== COLOR MODES ==========
#define MODE_STATIC       0
#define MODE_SMOOTH_FADE  1
#define MODE_RAINBOW      2
#define MODE_CUSTOM       3
#define MODE_SWEEP_RANDOM 4   // ← NEW

int colorMode = MODE_SMOOTH_FADE;

// Expanded premium palette — indices are EEPROM-safe (0..11).
const uint8_t colorsRGB[][3] = {
  {255,   0,   0},   // Red
  {255, 255,   0},   // Yellow
  {255, 165,   0},   // Orange
  {255, 192,  64},   // Gold
  {  0, 255, 255},   // Cyan
  {  0, 255,   0},   // Green
  {  0, 200, 140},   // Teal
  {128,   0, 255},   // Purple
  {255,   0, 255},   // Magenta
  {  0, 128, 255},   // Blue
  {  0,  40, 255},   // Deep Blue
  {255, 255, 255}    // White
};
const int TOTAL_COLORS = 12;

int staticColorIndex = 0;
unsigned long lastColorChange = 0;
int colorChangeInterval = 5;

uint8_t currR = 255, currG = 0, currB = 0;
uint8_t targetR = 255, targetG = 0, targetB = 0;
const float LERP_STEP = 0.08f;

uint16_t rainbowHue = 0;
int customR = 255, customG = 0, customB = 0;

// ========== RTTTL TONES (PROGMEM) ==========
// ১৩টা ringtone — PROGMEM এ রাখা হয়েছে RAM বাঁচাতে

const char TONE_00[] PROGMEM = "SmoothCriminal:d=16,o=5,b=100:32p,8c#,4c#,32c#.,32c#.,d#.,8d#.,32c#.,32d#.,e.,4e,32d#.,32e.,d#.,b.4,d#.,8c#.,c#.,32c#.,32c#.,32c#.,32c#.,d#.,8d#.,32c#.,32d#.,e.,8e.,32d#.,32e.,d#.,b.4,d#.,8c#.,c#.,32c#.,32c#.,32c#.,32c#.,d#.,8d#.,32c#.,32d#.,e.,8e.,32d#.,32e.,d#.,b.4";

const char TONE_01[] PROGMEM = "GrillaClnt:d=16,o=5,b=112:d#6,8c#6,8d#6,4a#,8p,f#,8g#,8a#,a#,a#,p,8d#6,4a#.,f#,8g#,8a#,g#,g#,p,8a#,4a#,p,f#,8f#,g#,8a#,f#,8f#,8f#,4d#,p,c#,8c#,d#,8d#.,8d#,8c#,d#,8d#.,8d#,8c#,d#,8d#.,8d#,8c#,d#,8d#.";

const char TONE_02[] PROGMEM = "Place4MyHead:b=125,o=6,d=8:32p,4a5,a5,16e,16e,e,f,e,d,4c,16a5,a#5,a#5,a#5,c,4a5,a5,16e,16e,e,f,e,d,4c,c,16a5,16a5,a#5,a#5,a#5,c,4a5,4a";

const char TONE_03[] PROGMEM = "MamboItaliano:d=4,o=5,b=140:32p,8e6,8p,8b,8p,b,p,8a,8p,8a,8a,8a,8b,8c6,8a,8e6,8p,8b,8p,b,8p,8b,8a,8p,8a,8a,8a,8b,8c6,8a,8e,8e,8e,8e,8e,8e,8e,8e,8e,8e,8e,8e,8g,8e,8e,16e";

const char TONE_04[] PROGMEM = "BadBoys:d=4,o=5,b=100:32p,8e,8e,8g,8e,8p,16e,16e,16e,16e,8g,8p,16g,16g,16g,16g,8g,16g,16a,16e.,32p,16e.,32p,16e.,32p";

const char TONE_05[] PROGMEM = "NSyncPop:b=125,o=6,d=8:32p,c#,16c#,c#.,b5,c#,4p,16b5,16b5,c#,16c#,c#.,b5,c#,4p,b5,c#,16c#,c#.,b5,c#,2p,b5,p,a5,p,g#5,p,b5,p,c#,16c#,c#.,b5,c#,2p,d#,16c#,c#.,b5,c#,2p,b5,16b5,b.5,c#,c#,2p,c#.,c#.,c#";

const char TONE_06[] PROGMEM = "GroovyBlue:d=32,o=6,b=112:p,16g,16a#,16g,a#.,16f.,a,8p.,a,8p.,a,8p,a,8p,a,8p.,a,a#.,a.,a,8a#,a,8g#,a,8p.,a,8p.,a,8p.,a,8p.,a,8p.,a,8p,16g,16a#,16g,16a#,16f,a,8p.,a,8p.,a,8p,a,8p,a,8p.";

const char TONE_07[] PROGMEM = "JingleBells:d=8,o=5,b=112:32p,a,a,4a,a,a,4a,a,c6,f.,16g,2a,a#,a#,a#.,16a#,a#,a,a.,16a,a,g,g,a,4g,4c6";

const char TONE_08[] PROGMEM = "Nokring:d=8,o=5,b=200:32p,d,d,d.,16p,2d,f,f,f.,16p,2c,d,d,d.,16p,2d,f,f,f.,16p,2c";

const char TONE_09[] PROGMEM = "RetroGroove:d=16,o=5,b=160:32p,e6,p,e6,p,e6,p,e6,p,4e6,d6,p,c6,p,a,p,2c6,8p,c6,p,d6,p,e6,p,e6,p,e6,p,e6,p,e6,p,e6,p,d6,p,c6,p,a,p,2c6,8p,c6,p,c6,p,d6,p,d6,p,d6,p,a,p,c6,p,d6,p,8e6,p,e6,f6,f6,e6,p,d6,p,c6,p,a,p,c6,p,8d6,8p";

const char TONE_10[] PROGMEM = "DoomLev1:d=32,o=5,b=56:f,f,f6,f,f,d#6,f,f,c#6,f,f,b,f,f,c6,c#6,f,f,f6,f,f,d#6,f,f,c#6,f,f,8b.,f,f,f6,f,f,d#6,f,f,c#6,f,f,b,f,f,c6,c#6,f,f,f6,f,f,d#6,f,f,c#6,f,f,8b.";

const char TONE_11[] PROGMEM = "Contra:d=4,o=6,b=200:a#5,a#5,c#,a#5,e.,d#.,c#,a#5,a#5,c#,a#5,e.,d#.,c#,a#5,a#5,c#,a#5,e.,d#.,c#,a#5,a#5,c#,a#5,d#.,e.,f,c,c,d#,c,f#.,f.,d#,c,c,d#,c,f#.,f.,d#,c,c,d#,c,f#.,f.,d#,c,c,d#,c,f.,f#.,g";

const char TONE_12[] PROGMEM = "MarReal:d=4,o=6,b=90:32p,c,g5,e,c,32p,8g,8f,8e,8d,8c,8c,8b5,8a5,8g5,c,d,e,16p,8g,8f,8e,8d,8c,g";

// Tone name labels for Web UI
const char TNAME_00[] PROGMEM = "Smooth Criminal";
const char TNAME_01[] PROGMEM = "Gorilla Clint";
const char TNAME_02[] PROGMEM = "Place 4 My Head";
const char TNAME_03[] PROGMEM = "Mambo Italiano";
const char TNAME_04[] PROGMEM = "Bad Boys";
const char TNAME_05[] PROGMEM = "NSync Pop";
const char TNAME_06[] PROGMEM = "Groovy Blue";
const char TNAME_07[] PROGMEM = "Jingle Bells";
const char TNAME_08[] PROGMEM = "Nokia Ring";
const char TNAME_09[] PROGMEM = "Retro Groove";
const char TNAME_10[] PROGMEM = "Doom Level 1";
const char TNAME_11[] PROGMEM = "Contra";
const char TNAME_12[] PROGMEM = "Mario Real";

const char* const TONES[]  PROGMEM = {TONE_00,TONE_01,TONE_02,TONE_03,TONE_04,TONE_05,TONE_06,TONE_07,TONE_08,TONE_09,TONE_10,TONE_11,TONE_12};
const char* const TNAMES[] PROGMEM = {TNAME_00,TNAME_01,TNAME_02,TNAME_03,TNAME_04,TNAME_05,TNAME_06,TNAME_07,TNAME_08,TNAME_09,TNAME_10,TNAME_11,TNAME_12};
#define TOTAL_TONES 13

// 0=Fixed, 1=Random, 2=Sequential
uint8_t toneMode  = 1;   // default: random
uint8_t toneIndex = 0;   // fixed mode এর জন্য
uint8_t toneSeqNext = 0; // sequential mode এর জন্য

// ========== SWEEP RANDOM STATE ==========
int  sweepPos = 0;
bool sweepForward = true;
unsigned long lastSweepStep = 0;
// animSpeed: 1=সবচেয়ে দ্রুত → 10=সবচেয়ে ধীর (Rainbow ও Sweep উভয়ের জন্য)
// Web UI slider থেকে save হয়, EEPROM এ থাকে
int animSpeed = 5;

uint32_t COLOR_ON;
uint32_t COLOR_OFF;

// ========== BRIGHTNESS ==========
int brightnessLevel = 80;
bool autoLDR = false;
int ldrMin = 1;    // রাতে minimum (1 = প্রায় বন্ধের মতো)
int ldrMax = 80;  // দিনে maximum
int ldrSmooth = 512;
// FIX: রাতে ঘর কখনো একদম pure অন্ধকার (analogRead=0) হয় না, তাই আগের linear
// map(0,1023,...) কখনো ldrMin (1) স্পর্শ করত না, একটা floor (~5) এ আটকে থাকত।
// এখন lowCut এর নিচে গেলেই সরাসরি ldrMin এবং highCut এর উপরে গেলেই সরাসরি ldrMax।
int ldrLowCut  = 150;  // এর নিচে/সমান হলে সরাসরি সর্বনিম্ন brightness (ldrMin)
int ldrHighCut = 900;  // এর উপরে/সমান হলে সরাসরি সর্বোচ্চ brightness (ldrMax)
int lastLdrRaw = 0;         // ওয়েব UI তে দেখানোর জন্য শেষ raw analogRead মান
int lastLdrBrightness = 0;  // ওয়েব UI তে দেখানোর জন্য শেষ প্রয়োগ করা brightness

// ========== D7 LIGHT CONTROL ==========
bool lightState = false;

// ========== ALARMS ==========
#define MAX_ALARMS 2
struct Alarm {
  byte hour;
  byte minute;
  bool enabled;
  byte toneIndex;  // কোন ringtone বাজবে (0-12)
};
Alarm alarms[MAX_ALARMS];
static byte lastAlarmMinute[MAX_ALARMS] = {255, 255};

// ========== HOURLY TONE TIME RANGE ==========
bool  hourlyToneRangeEnabled = false;  // false = সবসময় বাজবে
byte  hourlyToneStartHour    = 7;      // default: সকাল ৭টা
byte  hourlyToneEndHour      = 22;     // default: রাত ১০টা

bool alarmActive = false;
bool displayVisible = true;
unsigned long lastBlinkTime = 0;

// ========== DISPLAY OPTIONS ==========
byte hourFormat = 0;
bool showDateEnabled = true;
bool colonBlink = true;
int lastHour = -1;
bool hourlyBeepEnabled = true;
bool displayOn = true;
// FIX: আগে auto-schedule সরাসরি displayOn সেট করে দিত, ফলে user manually OFF
// করলেও schedule ON period-এ ঢুকলে display আবার চালু হয়ে যেত (manual override
// হারিয়ে যেত)। এখন দুটো আলাদা state — চূড়ান্ত displayOn = manual AND scheduled।
bool manualDisplayOn    = true;
bool scheduledDisplayOn = true;

void applyDisplayState() {
  bool newState = manualDisplayOn && scheduledDisplayOn;
  if (newState != displayOn) {
    displayOn = newState;
    if (!displayOn) { strip.clear(); strip.show(); }
  }
}

// ========== AUTO DISPLAY SCHEDULE ==========
bool autoDisplaySchedule = false;
byte displayOffHour = 22;
byte displayOffMinute = 0;
byte displayOnHour = 4;
byte displayOnMinute = 0;

// ========== DATE DISPLAY SETTINGS ==========
bool enableEnglishDate = true;
bool enableBanglaDate = true;

// ========== BANGLA DATE STRUCTURE ==========
struct BanglaDate { int day, month, year; };

// ========== WEB SERVER ==========
ESP8266WebServer server(80);
ESP8266HTTPUpdateServer httpUpdater;   // OTA updater
const char* ota_username = "sultan";   // OTA login username
const char* ota_password = "sultan88"; // OTA login password
const char* ap_ssid = "SULTAN DIGITAL CLOCK";
const char* ap_pass = "sultan88";

// ========== WEB UI PASSWORD ==========
char webui_pass[32] = "sultan88";   // default password
bool webuiPasswordEnabled = true;   // password protection on/off
#define ADDR_WEBUI_PASS      250    // 31 bytes: 250-280
#define ADDR_WEBUI_PASS_EN   281    // 1 byte: enabled flag
#define ADDR_ANIM_SPEED      282    // 1 byte: rainbow+sweep speed (1=fast → 10=slow)

// ========== WIFI CLIENT MODE ==========
char wifi_ssid[32] = "";
char wifi_pass[64] = "";
bool wifiClientMode = false;
bool wifiHasPassword = true;

// ========== STATIC IP SETTINGS ==========
bool useStaticIP = false;
byte staticIP_oct[4]  = {192, 168, 0, 108};
byte gatewayIP_oct[4] = {192, 168, 0,   1};
byte subnetIP_oct[4]  = {255, 255, 255, 0};

unsigned long lastWiFiCheck   = 0;
unsigned long lastWiFiAttempt = 0;
unsigned long lastNTPUpdate   = 0;

unsigned long lastHeapCheckMillis = 0;
const unsigned long HEAP_CHECK_INTERVAL = 30000UL;   // ৩০ সেকেন্ডে একবার চেক
const uint32_t HEAP_CRITICAL_BYTES      = 4000;       // এর নিচে নামলে restart
bool ntpSyncDone = false;

const unsigned long WIFI_CHECK_INTERVAL  = 15000UL;   // 15 সেকেন্ড
const unsigned long WIFI_RETRY_INTERVAL  = 30000UL;   // 30 সেকেন্ড
const unsigned long WIFI_RETRY_RESET_MS  = 300000UL;  // 5 মিনিট পর count reset
int wifiRetryCount = 0;
const int MAX_WIFI_RETRIES = 10;  // FIX: 3 → 10

// ========== EEPROM ADDRESSES ==========
#define ADDR_HOURFORMAT        0
#define ADDR_ALARMS            1
#define ADDR_SHOWDATE         10
#define ADDR_BRIGHTNESS       11
#define ADDR_COLORMODE        12
#define ADDR_STATICCOLOR      13
#define ADDR_COLORINTERVAL    14
#define ADDR_CUSTOMR          15
#define ADDR_CUSTOMG          16
#define ADDR_CUSTOMB          17
#define ADDR_AUTOLDR          18
#define ADDR_COLONBLINK       19
#define ADDR_LIGHTSTATE       20
#define ADDR_SHOW_ENGLISH_DATE 21
#define ADDR_SHOW_BANGLA_DATE  22
#define ADDR_MANUAL_DISPLAY    23  // FIX: manual display ON/OFF state persist করার জন্য (আগে reboot হলে হারিয়ে যেত)
#define ADDR_HOURLY_BEEP      24
#define ADDR_AUTO_DISPLAY_SCH 25
#define ADDR_DISP_OFF_HOUR    26
#define ADDR_DISP_OFF_MIN     27
#define ADDR_DISP_ON_HOUR     28
#define ADDR_DISP_ON_MIN      29
#define ADDR_LDR_LOWCUT       30   // 1 byte, scaled /4 (0-255 → 0-1020)
#define ADDR_LDR_HIGHCUT      31   // 1 byte, scaled /4 (0-255 → 0-1020)
#define ADDR_WIFI_SSID       100
#define ADDR_WIFI_PASS       132
#define ADDR_WIFI_ENABLE     196
#define ADDR_WIFI_HASPASS    197
#define ADDR_USE_STATIC_IP   198
#define ADDR_STATIC_IP       199
#define ADDR_GATEWAY_IP      203
#define ADDR_SUBNET_IP       207
// Tone settings (211-212 freed — Playlist moved to 283)
#define ADDR_TONE_MODE       243  // 0=fixed,1=random,2=sequential
#define ADDR_TONE_INDEX      244  // fixed tone index (0-12)
#define ADDR_ALARM_TONE_0    245  // alarm 0 ringtone index
#define ADDR_ALARM_TONE_1    246  // alarm 1 ringtone index
#define ADDR_TONE_RANGE_EN   247  // hourly tone time range enabled
#define ADDR_TONE_START_HR   248  // hourly tone start hour
#define ADDR_TONE_END_HR     249  // hourly tone end hour
// WebUI pass: 250-281, animSpeed: 282 (অপরিবর্তিত)

// FIX: Fresh/uninitialized EEPROM detect করার জন্য magic byte।
// address 510 remains unused; expanded playlist ends at 340 and EEPROM is 512 bytes.
#define ADDR_INIT_FLAG    510
#define EEPROM_INIT_MAGIC 0xA5

// ========== COLOR PLAYLIST ==========
#define MAX_PLAYLIST_STEPS   8
#define ADDR_PLAYLIST_ENABLE 283  // moved: was 211
#define ADDR_PLAYLIST_COUNT  284  // moved: was 212
#define ADDR_PLAYLIST_STEPS  285  // 8 steps × 8 bytes = 64 bytes (285-348) — বাইট-8 হলো colorChangeSec

struct PlaylistStep {
  uint8_t mode;       // 0=Static,1=SmoothFade,2=Rainbow,3=CustomRGB,4=SweepRandom
  uint8_t colorIndex; // preset color index
  uint8_t r, g, b;   // custom RGB
  uint8_t duration;   // সেকেন্ড (1-255)
  uint8_t speed;      // animation speed 1=দ্রুত → 10=ধীর (Rainbow/Sweep এর জন্য)
  uint8_t colorChangeSec; // Static/Smooth Fade: এই সেকেন্ড পরপর পরের প্যালেট কালারে বদলাবে
                          // (1-255, duration-এর বেশি হতে পারবে না; পুরো duration জুড়ে
                          // একটা রঙ দেখাতে চাইলে colorChangeSec == duration সেট করো)
};

bool          playlistEnabled   = false;
uint8_t       playlistCount     = 0;
PlaylistStep  playlistSteps[MAX_PLAYLIST_STEPS];
uint8_t       playlistCurrent   = 0;
unsigned long playlistStepStart = 0;

// Playlist-special transition state.
// When a playlist step is Smooth Fade, the selected step color becomes the
// target and the previous visible color becomes the starting point. Each
// sub-cycle (colorChangeSec long) gets its own cross-fade, so a step keeps
// cycling through palette colors for its full duration instead of fading
// once and then sitting still.
bool playlistSmoothActive = false;
uint8_t playlistFadeStartR = 255, playlistFadeStartG = 0, playlistFadeStartB = 0;
unsigned long playlistFadeStart = 0;
unsigned long playlistFadeDurationMs = 1000;

// Playlist Static/Smooth-Fade in-step color cycling (NEW: "Color change sec").
unsigned long playlistColorCycleStart = 0;  // when the current sub-color started
uint8_t       playlistCycleColorIndex = 0;  // palette index currently active in the cycle

// ========== FUNCTION PROTOTYPES ==========
void loadSettings();
void saveSettings();
void startWebUI();
void connectWiFiClient();
void checkWiFiConnection();
void checkHeapHealth();
void checkAlarm(DateTime now);
void showDigit(int index, int num);
void setColon(int pos[2], bool on);
void beepBuzzer(int times);
void updateBrightness();
void updateColorMode();
void updatePlaylist();
void updatePlaylistColorCycle();
void applyPlaylistStep(uint8_t idx);
void savePlaylist();
void loadPlaylist();
void updateSweepRandom();
uint32_t getSweepColor(int ledIndex);
void showDigitSweep(int index, int num);
void setColonSweep(int pos[2], bool on);
uint32_t Wheel(byte WheelPos);
bool validateAndSyncNTP();
void handleRoot();
void handleAbout();
void handleSync();
void handleNTPSync();
void handleSaveSettings();
void handleSaveAlarm();
void handleSaveBrightness();
void handleSaveColor();
void handleSaveWiFi();
void handleToggleLight();
void handleSaveDateSettings();
void handleToggleDisplay();
void handleSaveDisplaySchedule();
void handleSavePlaylist();
void handleTogglePlaylist();
void handleScanWiFi();
void handleSaveTone();
void handleTestTone();
void handleSaveToneRange();
void handleOTAPage();
void handleSettings();
void handleChangePassword();
void handleCheckAuth();
void playRTTTL(const char* rtttl_pgm);
void playHourlyTone();
void checkAutoDisplaySchedule(DateTime now);
BanglaDate getBanglaDate(DateTime engDate);
void displayEnglishDate(int day, int month, int year);
void displayBanglaDate(int day, int month, int year);
void showTime(int h, int m, int s);

// ========== BANGLA DATE CALCULATION (FIXED — সঠিক offset + Leap Year support) ==========
// Helper: leap year check
static bool isLeapYear(int y) {
  return (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0);
}

BanglaDate getBanglaDate(DateTime engDate) {
  BanglaDate bd;
  int day   = engDate.day();
  int month = engDate.month();
  int year  = engDate.year();

  if (month == 1) {
    bd.month = 9;  bd.day = day + 16;
    if (bd.day > 30) { bd.month = 10; bd.day -= 30; }
  } else if (month == 2) {
    // Magh (মাস ১০) সবসময় ৩০ দিনের, Jan15 থেকে শুরু — leap বছরেও এটা অপরিবর্তিত
    // থাকে কারণ leap day (Feb 29) আসে Magh শেষ হওয়ার পরে, Falgun-এর মধ্যে
    bd.month = 10; bd.day = day + 17;
    if (bd.day > 30) { bd.month = 11; bd.day -= 30; }
  } else if (month == 3) {
   bool leap = isLeapYear(year);
    int off = leap ? 16 : 15;
    int falgunDays = leap ? 30 : 29;
    bd.month = 11; bd.day = day + off;
    if (bd.day > falgunDays) { bd.month = 12; bd.day -= falgunDays; }
  } else if (month == 4) {
    if (day < 14) { bd.month = 12; bd.day = day + 17; }
    else          { bd.month =  1; bd.day = day - 13; }
  } else if (month == 5) {
    bd.month = 1;  bd.day = day + 17;
    if (bd.day > 31) { bd.month = 2; bd.day -= 31; }
  } else if (month == 6) {
    bd.month = 2;  bd.day = day + 17;
    if (bd.day > 31) { bd.month = 3; bd.day -= 31; }
  } else if (month == 7) {
    bd.month = 3;  bd.day = day + 16;
    if (bd.day > 31) { bd.month = 4; bd.day -= 31; }
  } else if (month == 8) {
    bd.month = 4;  bd.day = day + 16;
    if (bd.day > 31) { bd.month = 5; bd.day -= 31; }
  } else if (month == 9) {
    bd.month = 5;  bd.day = day + 16;
    if (bd.day > 31) { bd.month = 6; bd.day -= 31; }
  } else if (month == 10) {
    bd.month = 6;  bd.day = day + 15;
    if (bd.day > 31) { bd.month = 7; bd.day -= 31; }
  } else if (month == 11) {
    bd.month = 7;  bd.day = day + 15;
    if (bd.day > 30) { bd.month = 8; bd.day -= 30; }
  } else {
    bd.month = 8;  bd.day = day + 15;
    if (bd.day > 30) { bd.month = 9; bd.day -= 30; }
  }

  if (month > 4 || (month == 4 && day >= 14)) {
    bd.year = year - 593;
  } else {
    bd.year = year - 594;
  }
  return bd;
}
void displayEnglishDate(int day, int month, int year) {
  (void)year; // হার্ডওয়্যারে year দেখানোর জায়গা নেই
  showDigit(0, day/10);   showDigit(1, day%10);
  showDigit(2, month/10); showDigit(3, month%10);
  setColon(colon1, true);
}
void displayBanglaDate(int day, int month, int year) {
  (void)year; // হার্ডওয়্যারে year দেখানোর জায়গা নেই
  showDigit(0, day/10);   showDigit(1, day%10);
  showDigit(2, month/10); showDigit(3, month%10);
  setColon(colon1, true);
}
void showTime(int h, int m, int s) {
  int disp_h = h;
  if (hourFormat == 1) { disp_h = h % 12; if (disp_h == 0) disp_h = 12; }

  showDigit(0, disp_h/10); showDigit(1, disp_h%10);
  showDigit(2, m/10);      showDigit(3, m%10);

  bool colonOn = true;
  if (alarmActive)    colonOn = displayVisible;
  else if (colonBlink) colonOn = (s % 2 == 0);
  setColon(colon1, colonOn);
}

// ========== NTP VALIDATION & SYNC (FIXED) ==========
bool validateAndSyncNTP() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println((__FlashStringHelper*)STR_WIFI_NOT_CONNECTED);
    return false;
  }
  if (!rtcOK) {
    Serial.println((__FlashStringHelper*)STR_RTC_NOTAVAIL);
    return false;
  }

  // FIX: Retry loop — 3 attempts with 2s wait each
  for (int attempt = 1; attempt <= 3; attempt++) {
    Serial.print((__FlashStringHelper*)STR_NTP_REQUESTING);
    Serial.printf(" (attempt %d/3)\n", attempt);

    timeClient.forceUpdate();
    delay(2000);  // wait for UDP response

    unsigned long epochTime = timeClient.getEpochTime();

    if (epochTime < NTP_MIN_VALID_TIME) {
      Serial.print((__FlashStringHelper*)STR_NTP_INVALID);
      Serial.println(epochTime);
      continue;
    }
    if (epochTime < NTP_MIN_EXPECTED_TIME || epochTime > NTP_MAX_EXPECTED_TIME) {
      Serial.print((__FlashStringHelper*)STR_NTP_OUTOFRANGE);
      Serial.println(epochTime);
      continue;
    }

    // FIX: Add Bangladesh UTC+6 offset before saving to RTC
    unsigned long bdTime = epochTime + BANGLADESH_OFFSET;
    rtc.adjust(DateTime(bdTime));

    // Verify write
    DateTime now = rtc.now();
    if (now.unixtime() < NTP_MIN_VALID_TIME) {
      Serial.println((__FlashStringHelper*)STR_RTC_UPDATE_FAILED);
      continue;
    }

    Serial.println((__FlashStringHelper*)STR_NTP_SYNC_SUCCESS);
    Serial.print((__FlashStringHelper*)STR_NEW_TIME);
    Serial.printf("%04d/%02d/%02d %02d:%02d:%02d (BD UTC+6)\n",
                  now.year(), now.month(), now.day(),
                  now.hour(), now.minute(), now.second());
    return true;
  }

  Serial.println((__FlashStringHelper*)STR_NTP_FAILED);
  return false;
}

// ========== AUTO DISPLAY SCHEDULE ==========
void checkAutoDisplaySchedule(DateTime now) {
  if (!autoDisplaySchedule) return;

  int cur = now.hour()*60 + now.minute();
  int off = displayOffHour*60 + displayOffMinute;
  int on  = displayOnHour*60 + displayOnMinute;

  bool shouldBeOff;
  if (off < on) shouldBeOff = (cur >= off && cur < on);
  else          shouldBeOff = (cur >= off || cur < on);

  static bool lastScheduledState = true;
  if (shouldBeOff && lastScheduledState) {
    scheduledDisplayOn = false;
    lastScheduledState = false;
    applyDisplayState();
    Serial.println(F("Auto Display OFF by schedule"));
  } else if (!shouldBeOff && !lastScheduledState) {
    scheduledDisplayOn = true;
    lastScheduledState = true;
    applyDisplayState();
    Serial.println(F("Auto Display ON by schedule"));
  }
}
void checkHeapHealth() {
  unsigned long ms = millis();
  if (ms - lastHeapCheckMillis < HEAP_CHECK_INTERVAL) return;
  lastHeapCheckMillis = ms;

  uint32_t freeHeap = ESP.getFreeHeap();
  if (freeHeap < HEAP_CRITICAL_BYTES) {
    Serial.print(F("⚠️ Low heap detected: "));
    Serial.print(freeHeap);
    Serial.println(F(" bytes — restarting proactively to avoid a hang."));
    delay(200);           // Serial flush করার সময় দাও
    ESP.restart();
  }
}

void setup() {
  pinMode(BUZZER_PIN, OUTPUT); digitalWrite(BUZZER_PIN, LOW);
  pinMode(LDR_PIN, INPUT);
  pinMode(LIGHT_PIN, OUTPUT); digitalWrite(LIGHT_PIN, LOW);

  Serial.begin(115200);
  delay(100);
  Serial.println((__FlashStringHelper*)STR_STARTUP);
  Serial.println(F("SULTAN ADVANCED AUDIT | Color Palette + 8-Step Smart Playlist + Smooth Crossfade"));
  WiFi.setAutoReconnect(true);
  WiFi.persistent(false);
  WiFi.setOutputPower(17);

  strip.begin();
  strip.setBrightness(brightnessLevel);
  strip.clear();
  strip.show();

  COLOR_OFF = strip.Color(0, 0, 0);
  COLOR_ON  = strip.Color(255, 0, 0);

  Wire.begin(D2, D1);

  if (!rtc.begin()) {
    Serial.println((__FlashStringHelper*)STR_RTC_NOTFOUND);
    rtcOK = false;
  } else {
    rtcOK = true;
    Serial.println((__FlashStringHelper*)STR_RTC_INIT);
    if (rtc.lostPower()) {
      Serial.println((__FlashStringHelper*)STR_RTC_LOSTPOWER);
      rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
    }
  }

  EEPROM.begin(512);
  loadSettings();
  if (strcmp(webui_pass, "sultan88") == 0) {
    Serial.println(F("⚠️  WARNING: WebUI password is still the factory default (sultan88)."));
    Serial.println(F("⚠️  Change it from Settings → Web UI Password for better security."));
  }

  strip.setBrightness(brightnessLevel);
  digitalWrite(LIGHT_PIN, lightState ? HIGH : LOW);

  currR = colorsRGB[staticColorIndex][0];
  currG = colorsRGB[staticColorIndex][1];
  currB = colorsRGB[staticColorIndex][2];
  targetR = currR; targetG = currG; targetB = currB;

  // FIX 3: Sweep variables explicit initialization — best practice
  sweepPos      = 0;
  sweepForward  = true;
  lastSweepStep = 0;

  startWebUI();

  Serial.println((__FlashStringHelper*)STR_SETUP_COMPLETE);
  beepBuzzer(2);

  for (int i = 0; i < NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.Color(0, 255, 0));
    strip.show();
    delay(20);
  }
  strip.clear();
  strip.show();
}

// ========== MAIN LOOP ==========
void loop() {
  ESP.wdtFeed();
  server.handleClient();

  DateTime now;
  if (rtcOK) {
    now = rtc.now();
  } else {
    unsigned long s = millis() / 1000;
    now = DateTime(2025, 1, 1, (s/3600)%24, (s/60)%60, s%60);
  }
  checkAlarm(now);

  checkWiFiConnection();
  checkHeapHealth();   // FIX (v5b): heap ক্রিটিক্যাল হওয়ার আগেই graceful restart

  if (now.hour() != lastHour) {
    lastHour = now.hour();
    playHourlyTone();  // function নিজেই hourlyBeepEnabled ও time range check করে
  }

  checkAutoDisplaySchedule(now);
  updateBrightness();
  updatePlaylist();
  updatePlaylistColorCycle();
  updateColorMode();

  if (displayOn) {
    int h = now.hour(), m = now.minute(), s = now.second();

    if (colorMode == MODE_SWEEP_RANDOM) {
      // Sweep wave offset আপডেট
      updateSweepRandom();

      // Sweep mode: প্রতিটা LED আলাদা রঙ পাবে
      bool colonOn = alarmActive ? displayVisible : (colonBlink ? (s%2==0) : true);
      int disp_h = h;
      if (hourFormat == 1) { disp_h = h%12; if (disp_h==0) disp_h=12; }

      // FIX: showDateEnabled master toggle আগে কোথাও check হতো না, এখন দুটো
      // (English/Bangla) sub-toggle এর সাথে AND করা হলো
      if (s >= 11 && s <= 16 && showDateEnabled && enableEnglishDate) {
        showDigitSweep(0, now.day()/10);    showDigitSweep(1, now.day()%10);
        showDigitSweep(2, now.month()/10);  showDigitSweep(3, now.month()%10);
        setColonSweep(colon1, true);
      } else if (s >= 17 && s <= 21 && showDateEnabled && enableBanglaDate) {
        BanglaDate bd = getBanglaDate(now);
        showDigitSweep(0, bd.day/10);   showDigitSweep(1, bd.day%10);
        showDigitSweep(2, bd.month/10); showDigitSweep(3, bd.month%10);
        setColonSweep(colon1, true);
      } else {
        showDigitSweep(0, disp_h/10); showDigitSweep(1, disp_h%10);
        showDigitSweep(2, m/10);      showDigitSweep(3, m%10);
        setColonSweep(colon1, colonOn);
      }

    } else {
      // Normal modes
      // FIX: showDateEnabled master toggle এখন actual display logic-এ check হচ্ছে
      if      (s >= 0  && s <= 10)                                          showTime(h, m, s);
      else if (s >= 11 && s <= 16 && showDateEnabled && enableEnglishDate) displayEnglishDate(now.day(), now.month(), now.year());
      else if (s >= 17 && s <= 21 && showDateEnabled && enableBanglaDate)  { BanglaDate bd=getBanglaDate(now); displayBanglaDate(bd.day,bd.month,bd.year); }
      else                                               showTime(h, m, s);
    }

    strip.show();
  }

  delay(50);   // FIX: 100→50ms — smoother animation, better web server responsiveness
}

// ========== COLOR PLAYLIST ==========
void applyPlaylistStep(uint8_t idx) {
  if (idx >= playlistCount) return;

  PlaylistStep &s = playlistSteps[idx];
  uint8_t safeMode  = constrain(s.mode, 0, 4);
  uint8_t safeColor = constrain(s.colorIndex, 0, TOTAL_COLORS - 1);
  uint8_t safeSpeed = constrain(s.speed, 1, 10);
  uint8_t safeDur   = max((uint8_t)1, s.duration);
  // NEW: "Color change sec" — কত সেকেন্ড পরপর পরের প্যালেট কালারে যাবে।
  // 0/অবৈধ হলে পুরো duration-কে একটাই সাব-সাইকেল ধরো (পুরনো আচরণ)।
  uint8_t safeChangeSec = s.colorChangeSec;
  if (safeChangeSec < 1 || safeChangeSec > safeDur) safeChangeSec = safeDur;

  // Capture the currently visible color before changing the step.
  playlistFadeStartR = currR;
  playlistFadeStartG = currG;
  playlistFadeStartB = currB;

  colorMode = safeMode;
  staticColorIndex = safeColor;

  playlistSmoothActive = false;

  // Reset the in-step color-cycle tracker — every step starts its own
  // sub-cycle at its own selected color.
  playlistCycleColorIndex = safeColor;
  playlistColorCycleStart = millis();

  if (colorMode == MODE_CUSTOM) {
    customR = s.r;
    customG = s.g;
    customB = s.b;
    currR = customR;
    currG = customG;
    currB = customB;
    targetR = currR;
    targetG = currG;
    targetB = currB;
  }
  else if (colorMode == MODE_STATIC) {
    currR = colorsRGB[staticColorIndex][0];
    currG = colorsRGB[staticColorIndex][1];
    currB = colorsRGB[staticColorIndex][2];
    targetR = currR;
    targetG = currG;
    targetB = currB;
  }
  else if (colorMode == MODE_SMOOTH_FADE) {
    // Cross-fade from the previous step's actual output to this step's
    // first color, over one "Color change sec" sub-cycle. Further
    // sub-cycles (handled by updatePlaylistColorCycle()) keep fading to
    // the next palette color every colorChangeSec seconds until the
    // step's full duration elapses.
    targetR = colorsRGB[staticColorIndex][0];
    targetG = colorsRGB[staticColorIndex][1];
    targetB = colorsRGB[staticColorIndex][2];

    currR = playlistFadeStartR;
    currG = playlistFadeStartG;
    currB = playlistFadeStartB;

    playlistSmoothActive = true;
    playlistFadeStart = millis();
    playlistFadeDurationMs = (unsigned long)safeChangeSec * 1000UL;
    if (playlistFadeDurationMs < 250UL) playlistFadeDurationMs = 250UL;
  }
  else if (colorMode == MODE_RAINBOW) {
    animSpeed = safeSpeed;
  }
  else if (colorMode == MODE_SWEEP_RANDOM) {
    animSpeed = safeSpeed;
    sweepPos = 0;
    sweepForward = true;
    lastSweepStep = millis();
  }

  rainbowHue = 0;
  playlistStepStart = millis();

  Serial.printf("Playlist step %u -> mode=%u color=%u dur=%us cc=%us spd=%u\n",
                idx + 1, safeMode, safeColor, safeDur, safeChangeSec, safeSpeed);
}

// NEW: within a Static / Smooth Fade playlist step, cycle through palette
// colors every "Color change sec" seconds instead of showing a single
// color for the whole step duration.
void updatePlaylistColorCycle() {
  if (!playlistEnabled || playlistCount == 0) return;
  if (colorMode != MODE_STATIC && colorMode != MODE_SMOOTH_FADE) return;

  PlaylistStep &s = playlistSteps[playlistCurrent];
  uint8_t safeDur = max((uint8_t)1, s.duration);
  uint8_t changeSec = s.colorChangeSec;
  if (changeSec < 1 || changeSec > safeDur) changeSec = safeDur;

  unsigned long elapsed = (millis() - playlistColorCycleStart) / 1000UL;
  if (elapsed < changeSec) return;  // এখনো এই সাব-সাইকেলের মধ্যেই আছে

  playlistColorCycleStart = millis();
  playlistCycleColorIndex = (playlistCycleColorIndex + 1) % TOTAL_COLORS;

  uint8_t nr = colorsRGB[playlistCycleColorIndex][0];
  uint8_t ng = colorsRGB[playlistCycleColorIndex][1];
  uint8_t nb = colorsRGB[playlistCycleColorIndex][2];

  if (colorMode == MODE_STATIC) {
    // instant cut — updateColorMode() পরের প্রতি টিকে এই রঙই দেখাবে
    staticColorIndex = playlistCycleColorIndex;
    currR = nr; currG = ng; currB = nb;
    targetR = nr; targetG = ng; targetB = nb;
  } else {
    // MODE_SMOOTH_FADE — currently-visible color থেকে পরের রঙে নতুন fade শুরু করো
    playlistFadeStartR = currR;
    playlistFadeStartG = currG;
    playlistFadeStartB = currB;
    targetR = nr; targetG = ng; targetB = nb;

    playlistSmoothActive = true;
    playlistFadeStart = millis();
    playlistFadeDurationMs = (unsigned long)changeSec * 1000UL;
    if (playlistFadeDurationMs < 250UL) playlistFadeDurationMs = 250UL;
  }
}

void updatePlaylist() {
  if (!playlistEnabled || playlistCount == 0) return;
  unsigned long elapsed = (millis() - playlistStepStart) / 1000UL;
  if (elapsed >= playlistSteps[playlistCurrent].duration) {
    playlistCurrent = (playlistCurrent + 1) % playlistCount;
    applyPlaylistStep(playlistCurrent);
  }
}

void savePlaylist() {
  EEPROM.write(ADDR_PLAYLIST_ENABLE, playlistEnabled ? 1 : 0);
  EEPROM.write(ADDR_PLAYLIST_COUNT,  playlistCount);
  for (int i = 0; i < MAX_PLAYLIST_STEPS; i++) {
    int base = ADDR_PLAYLIST_STEPS + i * 8;  // 7 → 8 bytes per step
    EEPROM.write(base,     playlistSteps[i].mode);
    EEPROM.write(base + 1, playlistSteps[i].colorIndex);
    EEPROM.write(base + 2, playlistSteps[i].r);
    EEPROM.write(base + 3, playlistSteps[i].g);
    EEPROM.write(base + 4, playlistSteps[i].b);
    EEPROM.write(base + 5, playlistSteps[i].duration);
    EEPROM.write(base + 6, playlistSteps[i].speed);          // per-step speed
    EEPROM.write(base + 7, playlistSteps[i].colorChangeSec); // NEW: color change interval
  }
  EEPROM.commit();
  Serial.println(F("Playlist saved"));
}

void loadPlaylist() {
  playlistEnabled = EEPROM.read(ADDR_PLAYLIST_ENABLE) == 1;
  playlistCount   = EEPROM.read(ADDR_PLAYLIST_COUNT);
  if (playlistCount > MAX_PLAYLIST_STEPS) playlistCount = 0;
  for (int i = 0; i < MAX_PLAYLIST_STEPS; i++) {
    int base = ADDR_PLAYLIST_STEPS + i * 8;  // 7 → 8 bytes per step
    playlistSteps[i].mode           = EEPROM.read(base);
    if (playlistSteps[i].mode > MODE_SWEEP_RANDOM) playlistSteps[i].mode = MODE_STATIC;
    playlistSteps[i].colorIndex     = EEPROM.read(base + 1);
    if (playlistSteps[i].colorIndex >= TOTAL_COLORS) playlistSteps[i].colorIndex = 0;
    playlistSteps[i].r              = EEPROM.read(base + 2);
    playlistSteps[i].g              = EEPROM.read(base + 3);
    playlistSteps[i].b              = EEPROM.read(base + 4);
    playlistSteps[i].duration       = EEPROM.read(base + 5);
    playlistSteps[i].speed          = EEPROM.read(base + 6);  // per-step speed
    playlistSteps[i].colorChangeSec = EEPROM.read(base + 7);  // NEW
    if (playlistSteps[i].mode > 4)                playlistSteps[i].mode = 0;
    if (playlistSteps[i].colorIndex >= TOTAL_COLORS) playlistSteps[i].colorIndex = 0;
    if (playlistSteps[i].duration < 1)            playlistSteps[i].duration = 30;
    if (playlistSteps[i].speed < 1 || playlistSteps[i].speed > 10)
      playlistSteps[i].speed = 5;  // default: মাঝারি speed
    // NEW: colorChangeSec সবসময় 1..duration-এর মধ্যে থাকবে; অবৈধ/না-সেট থাকলে
    // পুরনো ফাইলের সাথে compatible রাখতে পুরো duration-কেই ধরে নাও (= 1টা রঙ)।
    if (playlistSteps[i].colorChangeSec < 1 ||
        playlistSteps[i].colorChangeSec > playlistSteps[i].duration)
      playlistSteps[i].colorChangeSec = playlistSteps[i].duration;
  }
  if (playlistEnabled && playlistCount > 0) {
    playlistCurrent   = 0;
    playlistStepStart = millis();
    applyPlaylistStep(0);
  }
}
uint32_t getSweepColor(int ledIndex) {
  // ledIndex + sweepOffset দিয়ে 0-255 range এ map করো
  uint8_t wpos = (uint8_t)(((ledIndex + sweepPos) * 255 / NUM_LEDS) & 0xFF);
  return Wheel(wpos);
}

// Sweep mode তে digit আঁকা — প্রতিটা segment আলাদা রঙ পাবে
void showDigitSweep(int index, int num) {
  if (!displayVisible && alarmActive) {
    int start = digitOffset[index];
    for (int i = 0; i < 7; i++) strip.setPixelColor(start + i, COLOR_OFF);
    return;
  }
  if (num < 0 || num > 10) num = 10;
  byte segments = digitsMatrix[num];
  int start = digitOffset[index];
  for (int i = 0; i < 7; i++) {
    if (segments & (1 << (6 - i))) {
      strip.setPixelColor(start + i, getSweepColor(start + i));
    } else {
      strip.setPixelColor(start + i, COLOR_OFF);
    }
  }
}

// Sweep mode তে colon আঁকা
void setColonSweep(int pos[2], bool on) {
  strip.setPixelColor(pos[0], on ? getSweepColor(pos[0]) : COLOR_OFF);
  strip.setPixelColor(pos[1], on ? getSweepColor(pos[1]) : COLOR_OFF);
}

void updateSweepRandom() {
  unsigned long nowMs = millis();
  // animSpeed 1=দ্রুত(20ms), 10=ধীর(200ms) — SWEEP_STEP_MS এর বদলে dynamic
  unsigned long sweepStepMs = (unsigned long)(animSpeed * 20);
  if (nowMs - lastSweepStep < sweepStepMs) return;
  lastSweepStep = nowMs;

  // sweepPos আপডেট — সামনে পেছনে যাবে
  if (sweepForward) {
    sweepPos++;
    if (sweepPos >= NUM_LEDS) { sweepForward = false; sweepPos = NUM_LEDS - 1; }
  } else {
    sweepPos--;
    if (sweepPos < 0) { sweepForward = true; sweepPos = 0; }
  }
  // COLOR_ON সেট করার দরকার নেই — showDigitSweep সরাসরি per-LED colour দেয়
}

// ========== WIFI CHECK ==========
void checkWiFiConnection() {
  unsigned long currentMillis = millis();
  if (!wifiClientMode || strlen(wifi_ssid) == 0) return;

  // FIX: CHECK_INTERVAL এখন 15s — disconnect হলে দ্রুত detect হবে
  if (currentMillis - lastWiFiCheck < WIFI_CHECK_INTERVAL) return;
  lastWiFiCheck = currentMillis;

  if (WiFi.status() == WL_CONNECTED) {
    // Connected — retry count reset করো
    wifiRetryCount = 0;
    // Hourly NTP sync
    if (currentMillis - lastNTPUpdate >= 3600000UL) {
      lastNTPUpdate = currentMillis;
      if (validateAndSyncNTP()) Serial.println((__FlashStringHelper*)STR_NTP_HOURLY);
      else Serial.println(F("Hourly NTP failed"));
    }
    return;
  }

  // ---- WiFi disconnected ----
  Serial.println((__FlashStringHelper*)STR_WIFI_DISCONNECTED);
  ntpSyncDone = false;

  // FIX: 5 মিনিট পর retry count reset (আগে ছিল 10 মিনিট)
  if (currentMillis - lastWiFiAttempt >= WIFI_RETRY_RESET_MS) {
    wifiRetryCount = 0;
    Serial.println(F("WiFi retry count reset after 5 min"));
  }

  // FIX: RETRY_INTERVAL এখন 30s (আগে ছিল 120s)
  if (currentMillis - lastWiFiAttempt < WIFI_RETRY_INTERVAL) return;

  // FIX: MAX_RETRIES এখন 10 (আগে ছিল 3)
  if (wifiRetryCount >= MAX_WIFI_RETRIES) {
    Serial.printf("Max retries (%d) reached. Next reset in %lus\n",
      MAX_WIFI_RETRIES,
      (WIFI_RETRY_RESET_MS - (currentMillis - lastWiFiAttempt)) / 1000UL);
    return;
  }

  wifiRetryCount++;
  lastWiFiAttempt = currentMillis;
  Serial.printf("WiFi reconnect attempt %d/%d\n", wifiRetryCount, MAX_WIFI_RETRIES);

  WiFi.disconnect();
  delay(500);

  if (useStaticIP) {
    IPAddress sip(staticIP_oct[0],  staticIP_oct[1],  staticIP_oct[2],  staticIP_oct[3]);
    IPAddress gip(gatewayIP_oct[0], gatewayIP_oct[1], gatewayIP_oct[2], gatewayIP_oct[3]);
    IPAddress snip(subnetIP_oct[0], subnetIP_oct[1],  subnetIP_oct[2],  subnetIP_oct[3]);
    bool cfgOK = WiFi.config(sip, gip, snip, IPAddress(8,8,8,8));
    if (!cfgOK) {
      Serial.println(F("Static IP config failed during reconnect! Using DHCP."));
      WiFi.config(IPAddress(0,0,0,0), IPAddress(0,0,0,0), IPAddress(0,0,0,0));
    }
  }

  if (wifiHasPassword && strlen(wifi_pass) > 0) WiFi.begin(wifi_ssid, wifi_pass);
  else WiFi.begin(wifi_ssid);
  for (int i = 0; i < 20 && WiFi.status() != WL_CONNECTED; i++) {
    for (int k = 0; k < 100; k++) { server.handleClient(); delay(10); }
    // FIX: এই wait loop-এর ভিতরেও প্রতি সেকেন্ডে অ্যালার্ম check করা হচ্ছে —
    // নইলে reconnect চলাকালীন (সর্বোচ্চ ২০ সেকেন্ড) ঠিক অ্যালার্মের মিনিটে
    // পড়লে সেটা পুরোপুরি miss হয়ে যেত
    if (rtcOK) checkAlarm(rtc.now());
    Serial.print(F(".")); ESP.wdtFeed();
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println((__FlashStringHelper*)STR_WIFI_RECONNECTED);
    Serial.print((__FlashStringHelper*)STR_IP_ADDRESS);
    Serial.println(WiFi.localIP());
    wifiRetryCount = 0;
    if (!ntpSyncDone) {
      timeClient.begin();
      delay(500);
    }
    if (validateAndSyncNTP()) {
      ntpSyncDone = true;
      lastNTPUpdate = currentMillis;
      beepBuzzer(2);
    }
  } else {
    Serial.println((__FlashStringHelper*)STR_WIFI_RECONNECT_FAILED);
    Serial.printf("Next retry in %lus\n", WIFI_RETRY_INTERVAL / 1000UL);
  }
}

// ========== BRIGHTNESS ==========
void updateBrightness() {
  if (autoLDR) {
    int ldrValue = analogRead(LDR_PIN);
    ldrSmooth = (ldrSmooth * 9 + ldrValue) / 10;
    int brightness;
    if (ldrSmooth <= ldrLowCut) {
      brightness = ldrMin;                 // যথেষ্ট অন্ধকার হলেই সরাসরি minimum
    } else if (ldrSmooth >= ldrHighCut) {
      brightness = ldrMax;                 // যথেষ্ট আলো হলেই সরাসরি maximum
    } else {
      brightness = map(ldrSmooth, ldrLowCut, ldrHighCut, ldrMin, ldrMax);
    }
    brightness = constrain(brightness, ldrMin, ldrMax);
    strip.setBrightness(brightness);
    lastLdrRaw = ldrSmooth;
    lastLdrBrightness = brightness;
  } else {
    strip.setBrightness(brightnessLevel);
  }
}

// ========== COLOR MODE UPDATE ==========
void updateColorMode() {
  switch (colorMode) {
    case MODE_STATIC:
      staticColorIndex = constrain(staticColorIndex, 0, TOTAL_COLORS - 1);
      currR = colorsRGB[staticColorIndex][0];
      currG = colorsRGB[staticColorIndex][1];
      currB = colorsRGB[staticColorIndex][2];
      COLOR_ON = strip.Color(currR, currG, currB);
      break;

    case MODE_SMOOTH_FADE:
      if (playlistEnabled && playlistSmoothActive) {
        // Playlist-specific full-duration cross-fade.
        unsigned long elapsed = millis() - playlistFadeStart;
        float t = (playlistFadeDurationMs == 0)
                   ? 1.0f
                   : (float)elapsed / (float)playlistFadeDurationMs;
        if (t >= 1.0f) {
          t = 1.0f;
          playlistSmoothActive = false;
        }

        currR = (uint8_t)((float)playlistFadeStartR +
                          ((float)targetR - (float)playlistFadeStartR) * t);
        currG = (uint8_t)((float)playlistFadeStartG +
                          ((float)targetG - (float)playlistFadeStartG) * t);
        currB = (uint8_t)((float)playlistFadeStartB +
                          ((float)targetB - (float)playlistFadeStartB) * t);
      } else {
        // Normal standalone Smooth Fade behavior.
        if (millis() - lastColorChange >=
            (unsigned long)colorChangeInterval * 1000UL) {
          lastColorChange = millis();
          staticColorIndex = (staticColorIndex + 1) % TOTAL_COLORS;
          targetR = colorsRGB[staticColorIndex][0];
          targetG = colorsRGB[staticColorIndex][1];
          targetB = colorsRGB[staticColorIndex][2];
        }

        int dR = (int)targetR - (int)currR;
        int dG = (int)targetG - (int)currG;
        int dB = (int)targetB - (int)currB;

        if (dR != 0) currR = (uint8_t)constrain((int)currR +
          (dR > 0 ? max(1, abs(dR) / 12) : -max(1, abs(dR) / 12)), 0, 255);
        if (dG != 0) currG = (uint8_t)constrain((int)currG +
          (dG > 0 ? max(1, abs(dG) / 12) : -max(1, abs(dG) / 12)), 0, 255);
        if (dB != 0) currB = (uint8_t)constrain((int)currB +
          (dB > 0 ? max(1, abs(dB) / 12) : -max(1, abs(dB) / 12)), 0, 255);
      }

      COLOR_ON = strip.Color(currR, currG, currB);
      break;

    case MODE_RAINBOW: {
      static uint8_t rainbowSkip = 0;
      rainbowSkip++;
      if (rainbowSkip >= constrain(animSpeed, 1, 10)) {
        rainbowSkip = 0;
        rainbowHue = (uint16_t)(rainbowHue + 50U);
      }
      COLOR_ON = Wheel((uint8_t)(rainbowHue >> 8));
      break;
    }

    case MODE_CUSTOM:
      currR = constrain(customR, 0, 255);
      currG = constrain(customG, 0, 255);
      currB = constrain(customB, 0, 255);
      COLOR_ON = strip.Color(currR, currG, currB);
      break;

    case MODE_SWEEP_RANDOM:
      // Per-LED colors are generated in updateSweepRandom()/showDigitSweep().
      break;

    default:
      colorMode = MODE_SMOOTH_FADE;
      playlistSmoothActive = false;
      break;
  }
}

uint32_t Wheel(byte WheelPos) {
  WheelPos = 255 - WheelPos;
  if (WheelPos < 85)  return strip.Color(255-WheelPos*3, 0, WheelPos*3);
  if (WheelPos < 170) { WheelPos -= 85; return strip.Color(0, WheelPos*3, 255-WheelPos*3); }
  WheelPos -= 170;
  return strip.Color(WheelPos*3, 255-WheelPos*3, 0);
}

// ========== ALARM CHECK (FIXED — uses flag to prevent re-trigger) ==========
void checkAlarm(DateTime now) {
  static int  triggeredAlarmIdx = -1;
  static unsigned long alarmStartTime = 0;

  if (!alarmActive) {
    for (int i = 0; i < MAX_ALARMS; i++) {
      // FIX: alarm-এর নির্ধারিত মিনিট পার হয়ে গেলে flag reset করে দাও,
      // নইলে lastAlarmMinute[i] চিরকাল আটকে থেকে পরের দিন আর অ্যালার্ম বাজবে না
      bool atAlarmTime = (now.hour() == alarms[i].hour && now.minute() == alarms[i].minute);
      if (!atAlarmTime) {
        lastAlarmMinute[i] = 255;
        continue;
      }
      if (alarms[i].enabled &&
          atAlarmTime &&
          now.second() < 10 &&
          lastAlarmMinute[i] != now.minute()) {
        lastAlarmMinute[i] = now.minute();
        alarmActive = true;
        triggeredAlarmIdx = i;
        Serial.println((__FlashStringHelper*)STR_ALARM_TRIGGER);
        // Alarm নির্ধারিত ringtone বাজাও (2 বার — 3 থেকে কমানো হয়েছে blocking কমাতে)
        byte aTone = constrain(alarms[i].toneIndex, 0, TOTAL_TONES - 1);
        const char* pgmPtr = (const char*)pgm_read_ptr(&TONES[aTone]);
        for (int rep = 0; rep < 2; rep++) {
          if (pgmPtr) playRTTTL(pgmPtr);
          delay(300);
          ESP.wdtFeed();
          server.handleClient();
        }
        // ringtone শেষ হওয়ার পর থেকে ৩০ সেকেন্ড blink
        alarmStartTime = millis();
        // FIX: break সরানো হয়েছে — একই মিনিটে একাধিক alarm set করা থাকলে
        // আগে শুধু প্রথমটাই বাজত, এখন loop চলতে থাকবে বাকিগুলোও check করার জন্য
      }
    }
  }

  if (alarmActive) {
    unsigned long cm = millis();
    // ringtone বাজানো শেষ — display blink করতে থাকবে ৩০ সেকেন্ড
    if (cm - alarmStartTime >= 30000UL) {
      alarmActive = false;
      digitalWrite(BUZZER_PIN, LOW);
      triggeredAlarmIdx = -1;
      Serial.println((__FlashStringHelper*)STR_ALARM_STOP);
    }
    if (cm - lastBlinkTime >= 500) { lastBlinkTime = cm; displayVisible = !displayVisible; }
  } else {
    displayVisible = true;
  }
}

// ========== SHOW DIGIT ==========
void showDigit(int index, int num) {
  if (!displayVisible && alarmActive) {
    int start = digitOffset[index];
    for (int i = 0; i < 7; i++) strip.setPixelColor(start+i, COLOR_OFF);
    return;
  }
  if (num < 0 || num > 10) num = 10;
  byte segments = digitsMatrix[num];
  int start = digitOffset[index];
  for (int i = 0; i < 7; i++) {
    strip.setPixelColor(start+i, (segments & (1<<(6-i))) ? COLOR_ON : COLOR_OFF);
  }
}

void setColon(int pos[2], bool on) {
  uint32_t c = on ? COLOR_ON : COLOR_OFF;
  strip.setPixelColor(pos[0], c);
  strip.setPixelColor(pos[1], c);
}

void beepBuzzer(int times) {
  // FIX: strip.clear() সরানো হয়েছে — display চালু থাকবে beep এর সময়
  // 250µs half-period = 2kHz — passive buzzer এ sharp ও loud beep
  for (int i = 0; i < times; i++) {
    for (int j = 0; j < 200; j++) {
      digitalWrite(BUZZER_PIN, HIGH); delayMicroseconds(250);
      digitalWrite(BUZZER_PIN, LOW);  delayMicroseconds(250);
    }
    delay(100);
  }
}

// ========== RTTTL TONE PLAYER ==========
// Manual PWM দিয়ে frequency বাজায় — tone() ব্যবহার করে না
// কারণ: ESP8266 এ tone() + WS2812B একসাথে timer conflict করে

void playNote(int freqHz, long durationMs) {
  if (freqHz <= 0) {
    delay(durationMs);
    return;
  }
  long period = 1000000L / freqHz;   // microseconds
  long halfP  = period / 2;
  long cycles = (durationMs * 1000L) / period;
  for (long i = 0; i < cycles; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delayMicroseconds(halfP);
    digitalWrite(BUZZER_PIN, LOW);
    delayMicroseconds(halfP);
  }
}

// MIDI note number থেকে frequency বের করে
// FIX 2: loop এর বদলে pow() — diff বড় হলেও O(1), সবসময় efficient
int midiToFreq(int midi) {
  // A4 = MIDI 69 = 440Hz, semitone ratio = 2^(1/12)
  if (midi == 69) return 440;
  return (int)(440.0f * powf(1.05946f, (float)(midi - 69)));
}

void playRTTTL(const char* rtttl_pgm) {
  // FIX: strip.clear() সরানো হয়েছে — display চালু থাকবে tone বাজার সময়
  // প্রতিটা note এর gap এ strip.show() করা হবে — display refresh হবে, flicker হবে না

  // PROGMEM থেকে পড়ো — বড় buffer
  char buf[320];
  strncpy_P(buf, rtttl_pgm, sizeof(buf) - 1);
  buf[sizeof(buf) - 1] = '\0';

  char* p = buf;

  // Name skip (: পর্যন্ত)
  while (*p && *p != ':') p++;
  if (!*p) return;
  p++;

  // Defaults
  int def_d = 4, def_o = 5, def_b = 120;

  // Default section
  while (*p && *p != ':') {
    while (*p == ' ') p++;
    if      (*p == 'd' && *(p+1) == '=') { p += 2; def_d = atoi(p); }
    else if (*p == 'o' && *(p+1) == '=') { p += 2; def_o = atoi(p); }
    else if (*p == 'b' && *(p+1) == '=') { p += 2; def_b = atoi(p); }
    while (*p && *p != ',' && *p != ':') p++;
    if (*p == ',') p++;
  }
  if (!*p) return;
  p++;

  // FIX (v5b - hang bug!): def_b (tempo, "b=") যদি ভুলবশত 0 বা negative parse
  // হয়, নিচের "60000/def_b" এ division-by-zero হতো — একই hang ঝুঁকি।
  if (def_b <= 0) def_b = 120;
  long wholenote = (60000L * 4) / def_b;

  // Notes
  while (*p) {
    while (*p == ' ') p++;
    if (!*p || *p == '\0') break;

    // Duration
    int dur = def_d;
    if (isdigit(*p)) {
      dur = 0;
      while (*p && isdigit(*p)) { dur = dur * 10 + (*p - '0'); p++; }
    }
    if (dur <= 0) dur = (def_d > 0) ? def_d : 4;

    // Note letter
    int semitone = -1; // -1 = pause
    switch (tolower(*p)) {
      case 'c': semitone = 0;  break;
      case 'd': semitone = 2;  break;
      case 'e': semitone = 4;  break;
      case 'f': semitone = 5;  break;
      case 'g': semitone = 7;  break;
      case 'a': semitone = 9;  break;
      case 'b': semitone = 11; break;
      case 'p': semitone = -1; break;
    }
    p++;

    // Sharp
    if (*p == '#') { semitone++; p++; }

    // Octave
    int oct = def_o;
    if (*p && isdigit(*p)) { oct = *p - '0'; p++; }

    // Duration ms
    long durationMs = wholenote / dur;
    if (*p == '.') { durationMs = durationMs * 3 / 2; p++; }

    // Play
    if (semitone >= 0) {
      int midiStd = semitone + (oct + 1) * 12;
      int freq = midiToFreq(midiStd);
      if (freq > 50 && freq < 8000) {
        playNote(freq, durationMs * 9 / 10);
      }
    }

    // Gap between notes — এই সময়ে strip.show() করো
    // delay এর আগে show() — display চালু থাকবে, note এর ফাঁকে refresh হবে
    strip.show();
    delay(durationMs / 10 + 1);

    if (*p == ',') p++;
    ESP.wdtFeed();
  }
  digitalWrite(BUZZER_PIN, LOW);
  strip.show(); // শেষে একবার আবার show করো
}

void playHourlyTone() {
  if (!hourlyBeepEnabled) return;

  // Time range check — নির্দিষ্ট সময়ের বাইরে বাজবে না
  if (hourlyToneRangeEnabled) {
    if (rtcOK) {
      DateTime rtcNow = rtc.now();
      byte curH = rtcNow.hour();
      if (hourlyToneStartHour <= hourlyToneEndHour) {
        // Normal range যেমন 7→22
        if (curH < hourlyToneStartHour || curH >= hourlyToneEndHour) return;
      } else {
        // Midnight-crossing range যেমন 20→8
        if (curH < hourlyToneStartHour && curH >= hourlyToneEndHour) return;
      }
    }
  }

  const char* pgmPtr = nullptr;

  if (toneMode == 0) {
    // Fixed — সবসময় একই tone
    pgmPtr = (const char*)pgm_read_ptr(&TONES[toneIndex % TOTAL_TONES]);
  } else if (toneMode == 1) {
    // Random — প্রতি ঘণ্টায় random
    pgmPtr = (const char*)pgm_read_ptr(&TONES[random(TOTAL_TONES)]);
  } else {
    // Sequential — ১→২→...→১০→১
    pgmPtr = (const char*)pgm_read_ptr(&TONES[toneSeqNext % TOTAL_TONES]);
    toneSeqNext = (toneSeqNext + 1) % TOTAL_TONES;
  }

  if (pgmPtr) playRTTTL(pgmPtr);
 }

bool requireAuth() {
  if (webuiPasswordEnabled) {
    if (!server.authenticate("admin", webui_pass)) {
      server.requestAuthentication(BASIC_AUTH, "Sultan Clock", "Password প্রয়োজন");
      return false;
    }
  }
  return true;
}

// ========== WEB HANDLERS ==========
void handleToggleLight() {
  if (!requireAuth()) return;
  lightState = !lightState;
  digitalWrite(LIGHT_PIN, lightState ? HIGH : LOW);
  saveSettings();
  Serial.print((__FlashStringHelper*)STR_D7_LIGHT);
  Serial.println(lightState ? F("ON") : F("OFF"));
  server.send(200, F("text/plain"), lightState ? F("ON") : F("OFF"));
}

void handleToggleDisplay() {
  if (!requireAuth()) return;
  manualDisplayOn = !manualDisplayOn;
  applyDisplayState();
  saveSettings();  // FIX: manual display state এখন EEPROM এ save হচ্ছে
  Serial.print(F("Display: "));
  Serial.println(displayOn ? F("ON") : F("OFF"));
  server.send(200, F("text/plain"), displayOn ? F("ON") : F("OFF"));
}

void handleSaveDisplaySchedule() {
  if (!requireAuth()) return;
  autoDisplaySchedule = server.arg(F("auto")) == F("1");
  displayOffHour   = constrain(server.arg(F("offh")).toInt(), 0, 23);
  displayOffMinute = constrain(server.arg(F("offm")).toInt(), 0, 59);
  displayOnHour    = constrain(server.arg(F("onh")).toInt(),  0, 23);
  displayOnMinute  = constrain(server.arg(F("onm")).toInt(),  0, 59);
  // FIX: schedule বন্ধ করলে আগের scheduled OFF state আটকে থাকতে পারত —
  // display চিরকাল off দেখাত যদিও schedule আর active নেই। disable করলে
  // scheduledDisplayOn কে true তে reset করে দেওয়া হচ্ছে।
  if (!autoDisplaySchedule) {
    scheduledDisplayOn = true;
    applyDisplayState();
  }
  saveSettings();
  Serial.println(F("Display schedule saved"));
  server.send(200, F("text/plain"), F("OK"));
}

// FIX: F() macro added
void handleSaveDateSettings() {
  if (!requireAuth()) return;
  enableEnglishDate = server.arg(F("eng"))    == F("1");
  enableBanglaDate  = server.arg(F("bangla")) == F("1");
  saveSettings();
  server.send(200, F("text/plain"), F("OK"));
}

void handleSync() {
  if (!requireAuth()) return;
  int y  = server.arg(F("y")).toInt();
  int mo = server.arg(F("mo")).toInt();
  int d  = server.arg(F("d")).toInt();
  int h  = server.arg(F("h")).toInt();
  int m  = server.arg(F("m")).toInt();
  int s  = server.arg(F("s")).toInt();
  if (y < 2025 || y > 2040) { server.send(400, F("text/plain"), F("Year out of range")); return; }
  if (rtcOK) { rtc.adjust(DateTime(y,mo,d,h,m,s)); Serial.println((__FlashStringHelper*)STR_TIME_SYNCED_PHONE); beepBuzzer(1); }
  server.send(200, F("text/plain"), F("OK"));
}

void handleNTPSync() {
  if (!requireAuth()) return;
  if (WiFi.status() != WL_CONNECTED) { server.send(400, F("text/plain"), F("WiFi not connected")); return; }
  if (!rtcOK)                         { server.send(400, F("text/plain"), F("RTC not available")); return; }
  if (validateAndSyncNTP()) { beepBuzzer(1); server.send(200, F("text/plain"), F("OK")); }
  else                       server.send(500, F("text/plain"), F("Failed"));
}

void handleSaveSettings() {
  if (!requireAuth()) return;
  // FIX (v5): আগে সরাসরি assign হতো, কোনো অবাঞ্ছিত মান এলে পরের reboot না হওয়া
  // পর্যন্ত ঠিক হতো না। এখন সাথে সাথেই 0/1-এ constrain করা হচ্ছে।
  hourFormat        = constrain(server.arg(F("f")).toInt(), 0, 1);
  showDateEnabled   = server.arg(F("sd")) == F("1");
  colonBlink        = server.arg(F("cb")) == F("1");
  hourlyBeepEnabled = server.arg(F("hb")) == F("1");
  saveSettings();
  server.send(200, F("text/plain"), F("OK"));
}

void handleSaveAlarm() {
  if (!requireAuth()) return;
  int i = server.arg(F("i")).toInt();
  if (i < 0 || i >= MAX_ALARMS) { server.send(400, F("text/plain"), F("Invalid")); return; }
  alarms[i].hour      = server.arg(F("h")).toInt();
  alarms[i].minute    = server.arg(F("m")).toInt();
  alarms[i].enabled   = server.arg(F("e")) == F("1");
  alarms[i].toneIndex = constrain(server.arg(F("t")).toInt(), 0, TOTAL_TONES - 1);
  lastAlarmMinute[i]  = 255; // reset so alarm can trigger again
  saveSettings();

  // FIX: একই সময়ে দুইটা alarm enable করলে বর্তমান architecture-এ শুধু প্রথমটাই
  // বাজে (দ্বিতীয়টা চুপচাপ কখনো বাজে না) — তাই save হয় ঠিকই কিন্তু user-কে
  // সাথে সাথে জানিয়ে দেওয়া হচ্ছে যাতে ভুল করে দুটো একই সময়ে রেখে না দেয়
  bool conflict = false;
  if (alarms[i].enabled) {
    int other = (i == 0) ? 1 : 0;
    if (alarms[other].enabled &&
        alarms[other].hour   == alarms[i].hour &&
        alarms[other].minute == alarms[i].minute) {
      conflict = true;
    }
  }
  server.send(200, F("text/plain"), conflict ? F("CONFLICT") : F("OK"));
}

void handleSaveBrightness() {
  if (!requireAuth()) return;
  brightnessLevel = constrain(server.arg(F("b")).toInt(), 1, 255);
  autoLDR = server.arg(F("a")) == F("1");
  if (server.hasArg(F("lc")) && server.hasArg(F("hc"))) {
    int lc = server.arg(F("lc")).toInt();
    int hc = server.arg(F("hc")).toInt();
    lc = constrain(lc, 0, 1023);
    hc = constrain(hc, 0, 1023);
    if (hc > lc + 20) { ldrLowCut = lc; ldrHighCut = hc; }
  }
  if (!autoLDR) strip.setBrightness(brightnessLevel);
  saveSettings();
  server.send(200, F("text/plain"), F("OK"));
}

void handleSaveColor() {
  if (!requireAuth()) return;
  colorMode          = constrain(server.arg(F("m")).toInt(),  0, 4);
  staticColorIndex   = constrain(server.arg(F("sc")).toInt(), 0, TOTAL_COLORS-1);
  colorChangeInterval= constrain(server.arg(F("ci")).toInt(), 1, 120);
  customR            = constrain(server.arg(F("r")).toInt(),  0, 255);
  customG            = constrain(server.arg(F("g")).toInt(),  0, 255);
  customB            = constrain(server.arg(F("b")).toInt(),  0, 255);
  animSpeed          = constrain(server.arg(F("spd")).toInt(), 1, 10);

  if (colorMode == MODE_STATIC || colorMode == MODE_SMOOTH_FADE) {
    currR = colorsRGB[staticColorIndex][0];
    currG = colorsRGB[staticColorIndex][1];
    currB = colorsRGB[staticColorIndex][2];
    targetR = currR; targetG = currG; targetB = currB;
  }
  if (colorMode == MODE_SWEEP_RANDOM) {
    sweepPos = 0; sweepForward = true;
  }
  saveSettings();
  server.send(200, F("text/plain"), F("OK"));
}

// FIX: server.handleClient() before restart so response reaches browser
void handleSaveWiFi() {
  if (!requireAuth()) return;
  wifiClientMode  = server.arg(F("en"))      == F("1");
  wifiHasPassword = server.arg(F("haspass")) == F("1");

  String ssid = server.arg(F("ssid"));
  String pass = server.arg(F("pass"));
  ssid.toCharArray(wifi_ssid, 32);
  wifi_ssid[31] = '\0'; // FIX: null terminator

  if (wifiHasPassword) { pass.toCharArray(wifi_pass, 64); wifi_pass[63] = '\0'; }
  else                   memset(wifi_pass, 0, 64);

  useStaticIP = server.arg(F("staticen")) == F("1");
  sscanf(server.arg(F("sip")).c_str(),  "%hhu.%hhu.%hhu.%hhu", &staticIP_oct[0],  &staticIP_oct[1],  &staticIP_oct[2],  &staticIP_oct[3]);
  sscanf(server.arg(F("gip")).c_str(),  "%hhu.%hhu.%hhu.%hhu", &gatewayIP_oct[0], &gatewayIP_oct[1], &gatewayIP_oct[2], &gatewayIP_oct[3]);
  String snip = server.arg(F("snip"));
  if (snip.length() > 0)
    sscanf(snip.c_str(), "%hhu.%hhu.%hhu.%hhu", &subnetIP_oct[0], &subnetIP_oct[1], &subnetIP_oct[2], &subnetIP_oct[3]);

  saveSettings();
  Serial.println((__FlashStringHelper*)STR_WIFI_SETTINGS_SAVED);

  // FIX: send response first, flush, then restart
  server.send(200, F("text/plain"), F("OK"));
  server.handleClient();
  beepBuzzer(2);
  delay(500);
  ESP.restart();
}

// ========== LOAD / SAVE SETTINGS ==========
void loadSettings() {
  if (EEPROM.read(ADDR_INIT_FLAG) != EEPROM_INIT_MAGIC) {
    Serial.println(F("Fresh EEPROM detected — writing default settings..."));
    EEPROM.write(ADDR_INIT_FLAG, EEPROM_INIT_MAGIC);
    saveSettings();   // current (ডিফল্ট) RAM ভ্যালুগুলো EEPROM এ লিখে ফেলা হচ্ছে
    savePlaylist();
  }

  hourFormat = EEPROM.read(ADDR_HOURFORMAT);
  if (hourFormat > 1) hourFormat = 0;

  for (int i = 0; i < MAX_ALARMS; i++) {
    alarms[i].hour    = EEPROM.read(ADDR_ALARMS + i*3);
    alarms[i].minute  = EEPROM.read(ADDR_ALARMS + i*3 + 1);
    alarms[i].enabled = EEPROM.read(ADDR_ALARMS + i*3 + 2) == 1;
    if (alarms[i].hour > 23) alarms[i].hour = 0;
    if (alarms[i].minute > 59) alarms[i].minute = 0;
  }

  showDateEnabled    = EEPROM.read(ADDR_SHOWDATE) == 1;
  // FIX: manual display OFF আগে EEPROM এ save হতো না — power cycle/reboot হলে
  // display আবার নিজে থেকে ON হয়ে যেত। এখন load করে scheduledDisplayOn এর
  // সাথে মিলিয়ে আসল displayOn ঠিকভাবে সেট করা হচ্ছে।
  manualDisplayOn    = EEPROM.read(ADDR_MANUAL_DISPLAY) == 1;
  displayOn = manualDisplayOn && scheduledDisplayOn;
  brightnessLevel    = EEPROM.read(ADDR_BRIGHTNESS);
  if (brightnessLevel < 1 || brightnessLevel > 255) brightnessLevel = 80;

  colorMode = EEPROM.read(ADDR_COLORMODE);
  // FIX (v5): colorMode < 0 কখনোই সত্য হয় না — EEPROM.read() সবসময় 0-255
  // রিটার্ন করে (uint8_t), তাই সেই অংশটা dead code ছিল। শুধু আসল upper-bound
  // check-টাই রাখা হলো।
  if (colorMode > 4) colorMode = MODE_SMOOTH_FADE;

  staticColorIndex = EEPROM.read(ADDR_STATICCOLOR);
  if (staticColorIndex < 0 || staticColorIndex >= TOTAL_COLORS) staticColorIndex = 0;

  colorChangeInterval = EEPROM.read(ADDR_COLORINTERVAL);
  if (colorChangeInterval < 1 || colorChangeInterval > 120) colorChangeInterval = 5;

  // FIX (v5): customR/G/B এগুলো uint8_t, EEPROM.read() ও uint8_t রিটার্ন করে —
  // তাই ">255" চেক কখনোই true হতে পারত না (dead code), সরিয়ে ফেলা হলো।
  customR = EEPROM.read(ADDR_CUSTOMR);
  customG = EEPROM.read(ADDR_CUSTOMG);
  customB = EEPROM.read(ADDR_CUSTOMB);

  autoLDR          = EEPROM.read(ADDR_AUTOLDR)      == 1;
  colonBlink       = EEPROM.read(ADDR_COLONBLINK)   == 1;
  lightState       = EEPROM.read(ADDR_LIGHTSTATE)   == 1;
  hourlyBeepEnabled= EEPROM.read(ADDR_HOURLY_BEEP)  == 1;

  // LDR low/high cut calibration (রাতে যেন brightness আসলেই ldrMin এ নামে)
  {
    int lc = EEPROM.read(ADDR_LDR_LOWCUT);
    int hc = EEPROM.read(ADDR_LDR_HIGHCUT);
    ldrLowCut  = (lc == 255) ? 150 : lc * 4;
    ldrHighCut = (hc == 255) ? 900 : hc * 4;
    if (ldrHighCut <= ldrLowCut + 20) { ldrLowCut = 150; ldrHighCut = 900; } // sanity fallback
  }

  enableEnglishDate = EEPROM.read(ADDR_SHOW_ENGLISH_DATE) == 1;
  enableBanglaDate  = EEPROM.read(ADDR_SHOW_BANGLA_DATE)  == 1;

  autoDisplaySchedule = EEPROM.read(ADDR_AUTO_DISPLAY_SCH) == 1;
  displayOffHour   = EEPROM.read(ADDR_DISP_OFF_HOUR);
  displayOffMinute = EEPROM.read(ADDR_DISP_OFF_MIN);
  displayOnHour    = EEPROM.read(ADDR_DISP_ON_HOUR);
  displayOnMinute  = EEPROM.read(ADDR_DISP_ON_MIN);
  if (displayOffHour > 23)   displayOffHour = 22;
  if (displayOffMinute > 59) displayOffMinute = 0;
  if (displayOnHour > 23)    displayOnHour = 4;
  if (displayOnMinute > 59)  displayOnMinute = 0;

  wifiClientMode  = EEPROM.read(ADDR_WIFI_ENABLE)   == 1;
  wifiHasPassword = EEPROM.read(ADDR_WIFI_HASPASS)  == 1;
  useStaticIP     = EEPROM.read(ADDR_USE_STATIC_IP) == 1;

  for (int i = 0; i < 4; i++) {
    staticIP_oct[i]  = EEPROM.read(ADDR_STATIC_IP  + i);
    gatewayIP_oct[i] = EEPROM.read(ADDR_GATEWAY_IP + i);
    subnetIP_oct[i]  = EEPROM.read(ADDR_SUBNET_IP  + i);
  }
  if (staticIP_oct[0]  == 0 || staticIP_oct[0]  == 255) { staticIP_oct[0]=192; staticIP_oct[1]=168; staticIP_oct[2]=0; staticIP_oct[3]=108; }
  if (gatewayIP_oct[0] == 0 || gatewayIP_oct[0] == 255) { gatewayIP_oct[0]=192; gatewayIP_oct[1]=168; gatewayIP_oct[2]=0; gatewayIP_oct[3]=1; }
  if (subnetIP_oct[0]  == 0) { subnetIP_oct[0]=255; subnetIP_oct[1]=255; subnetIP_oct[2]=255; subnetIP_oct[3]=0; }

  for (int i = 0; i < 32; i++) wifi_ssid[i] = EEPROM.read(ADDR_WIFI_SSID + i);
  wifi_ssid[31] = '\0'; // FIX: null terminator

  for (int i = 0; i < 64; i++) wifi_pass[i] = EEPROM.read(ADDR_WIFI_PASS + i);
  wifi_pass[63] = '\0'; // FIX: null terminator

  Serial.println((__FlashStringHelper*)STR_SETTINGS_LOADED);

  toneMode  = EEPROM.read(ADDR_TONE_MODE);
  toneIndex = EEPROM.read(ADDR_TONE_INDEX);
  if (toneMode > 2)              toneMode  = 1;
  if (toneIndex >= TOTAL_TONES)  toneIndex = 0;

  // Alarm ringtone index
  for (int i = 0; i < MAX_ALARMS; i++) {
    alarms[i].toneIndex = EEPROM.read(ADDR_ALARM_TONE_0 + i);
    if (alarms[i].toneIndex >= TOTAL_TONES) alarms[i].toneIndex = 0;
  }

  // Hourly tone time range
  hourlyToneRangeEnabled = EEPROM.read(ADDR_TONE_RANGE_EN) == 1;
  hourlyToneStartHour    = EEPROM.read(ADDR_TONE_START_HR);
  hourlyToneEndHour      = EEPROM.read(ADDR_TONE_END_HR);
  if (hourlyToneStartHour > 23) hourlyToneStartHour = 7;
  if (hourlyToneEndHour   > 23) hourlyToneEndHour   = 22;

  // Web UI password
  webuiPasswordEnabled = EEPROM.read(ADDR_WEBUI_PASS_EN) != 0;
  for (int i = 0; i < 31; i++) webui_pass[i] = EEPROM.read(ADDR_WEBUI_PASS + i);
  webui_pass[31] = '\0';

  // Animation speed (Rainbow + Sweep)
  animSpeed = EEPROM.read(ADDR_ANIM_SPEED);
  if (animSpeed < 1 || animSpeed > 10) animSpeed = 5;

  {
    bool valid = (strlen(webui_pass) >= 4);
    if (valid) {
      for (int i = 0; i < (int)strlen(webui_pass); i++) {
        if ((uint8_t)webui_pass[i] > 126 || (uint8_t)webui_pass[i] < 32) {
          valid = false;
          break;
        }
      }
    }
    if (!valid) {
      strcpy(webui_pass, "sultan88");
      webuiPasswordEnabled = true;
      // EEPROM এ সাথে সাথে save করো যাতে পরের boot এও ঠিক থাকে
      for (int i = 0; i < 31; i++) EEPROM.write(ADDR_WEBUI_PASS + i, webui_pass[i]);
      EEPROM.write(ADDR_WEBUI_PASS_EN, 1);
      EEPROM.commit();
      Serial.println(F("webui_pass: EEPROM garbage detected → reset to sultan88"));
    }
  }
  // ===== END BUG FIX =====

  loadPlaylist();
}

void saveSettings() {
  EEPROM.write(ADDR_HOURFORMAT, hourFormat);
  for (int i = 0; i < MAX_ALARMS; i++) {
    EEPROM.write(ADDR_ALARMS + i*3,     alarms[i].hour);
    EEPROM.write(ADDR_ALARMS + i*3 + 1, alarms[i].minute);
    EEPROM.write(ADDR_ALARMS + i*3 + 2, alarms[i].enabled ? 1 : 0);
  }
  EEPROM.write(ADDR_SHOWDATE,       showDateEnabled ? 1 : 0);
  EEPROM.write(ADDR_MANUAL_DISPLAY, manualDisplayOn ? 1 : 0);
  EEPROM.write(ADDR_BRIGHTNESS,     brightnessLevel);
  EEPROM.write(ADDR_COLORMODE,      colorMode);
  EEPROM.write(ADDR_STATICCOLOR,    staticColorIndex);
  EEPROM.write(ADDR_COLORINTERVAL,  colorChangeInterval);
  EEPROM.write(ADDR_CUSTOMR,        customR);
  EEPROM.write(ADDR_CUSTOMG,        customG);
  EEPROM.write(ADDR_CUSTOMB,        customB);
  EEPROM.write(ADDR_AUTOLDR,        autoLDR ? 1 : 0);
  EEPROM.write(ADDR_LDR_LOWCUT,     constrain(ldrLowCut  / 4, 0, 254));
  EEPROM.write(ADDR_LDR_HIGHCUT,    constrain(ldrHighCut / 4, 0, 254));
  EEPROM.write(ADDR_COLONBLINK,     colonBlink ? 1 : 0);
  EEPROM.write(ADDR_LIGHTSTATE,     lightState ? 1 : 0);
  EEPROM.write(ADDR_HOURLY_BEEP,    hourlyBeepEnabled ? 1 : 0);
  EEPROM.write(ADDR_SHOW_ENGLISH_DATE, enableEnglishDate ? 1 : 0);
  EEPROM.write(ADDR_SHOW_BANGLA_DATE,  enableBanglaDate  ? 1 : 0);
  EEPROM.write(ADDR_AUTO_DISPLAY_SCH, autoDisplaySchedule ? 1 : 0);
  EEPROM.write(ADDR_DISP_OFF_HOUR,  displayOffHour);
  EEPROM.write(ADDR_DISP_OFF_MIN,   displayOffMinute);
  EEPROM.write(ADDR_DISP_ON_HOUR,   displayOnHour);
  EEPROM.write(ADDR_DISP_ON_MIN,    displayOnMinute);
  EEPROM.write(ADDR_WIFI_ENABLE,    wifiClientMode  ? 1 : 0);
  EEPROM.write(ADDR_WIFI_HASPASS,   wifiHasPassword ? 1 : 0);
  EEPROM.write(ADDR_USE_STATIC_IP,  useStaticIP     ? 1 : 0);
  for (int i = 0; i < 4; i++) {
    EEPROM.write(ADDR_STATIC_IP  + i, staticIP_oct[i]);
    EEPROM.write(ADDR_GATEWAY_IP + i, gatewayIP_oct[i]);
    EEPROM.write(ADDR_SUBNET_IP  + i, subnetIP_oct[i]);
  }
  for (int i = 0; i < 32; i++) EEPROM.write(ADDR_WIFI_SSID + i, wifi_ssid[i]);
  for (int i = 0; i < 64; i++) EEPROM.write(ADDR_WIFI_PASS + i, wifi_pass[i]);
  EEPROM.write(ADDR_TONE_MODE,  toneMode);
  EEPROM.write(ADDR_TONE_INDEX, toneIndex);
  for (int i = 0; i < MAX_ALARMS; i++)
    EEPROM.write(ADDR_ALARM_TONE_0 + i, alarms[i].toneIndex);
  EEPROM.write(ADDR_TONE_RANGE_EN, hourlyToneRangeEnabled ? 1 : 0);
  EEPROM.write(ADDR_TONE_START_HR, hourlyToneStartHour);
  EEPROM.write(ADDR_TONE_END_HR,   hourlyToneEndHour);
  EEPROM.write(ADDR_ANIM_SPEED,     (byte)animSpeed);
  EEPROM.write(ADDR_WEBUI_PASS_EN, webuiPasswordEnabled ? 1 : 0);
  for (int i = 0; i < 31; i++) EEPROM.write(ADDR_WEBUI_PASS + i, webui_pass[i]);
  EEPROM.commit();
  Serial.println((__FlashStringHelper*)STR_SETTINGS_SAVED);
}

// ========== WIFI CONNECT ==========
void connectWiFiClient() {
  Serial.print((__FlashStringHelper*)STR_WIFI_CONNECTING);
  Serial.println(wifi_ssid);

  WiFi.mode(WIFI_AP_STA);
  WiFi.setAutoReconnect(true);   // FIX: true — ESP8266 background এ reconnect করবে
  WiFi.persistent(false);

  // FIX Static IP: config করার আগে disconnect করো,
  // না হলে পুরনো DHCP lease এর সাথে conflict হতে পারে
  WiFi.disconnect();
  delay(300);

  if (useStaticIP) {
    IPAddress sip(staticIP_oct[0],  staticIP_oct[1],  staticIP_oct[2],  staticIP_oct[3]);
    IPAddress gip(gatewayIP_oct[0], gatewayIP_oct[1], gatewayIP_oct[2], gatewayIP_oct[3]);
    IPAddress snip(subnetIP_oct[0], subnetIP_oct[1],  subnetIP_oct[2],  subnetIP_oct[3]);
    IPAddress dns(8, 8, 8, 8);
    bool cfgOK = WiFi.config(sip, gip, snip, dns);
    if (!cfgOK) {
      // FIX Static IP: config fail হলে Serial এ error দেখাও এবং DHCP তে fallback করো
      Serial.println(F("Static IP config failed! Falling back to DHCP."));
      WiFi.config(IPAddress(0,0,0,0), IPAddress(0,0,0,0), IPAddress(0,0,0,0));
    } else {
      Serial.print(F("Static IP: "));
      Serial.println(sip);
    }
  }

  if (wifiHasPassword && strlen(wifi_pass) > 0) WiFi.begin(wifi_ssid, wifi_pass);
  else WiFi.begin(wifi_ssid);
  Serial.print(F("Waiting for connection"));
  for (int i = 0; i < 30 && WiFi.status() != WL_CONNECTED; i++) {
    for (int k = 0; k < 100; k++) { server.handleClient(); delay(10); }
    Serial.print(F(".")); ESP.wdtFeed();
    // status log করো যাতে debug সহজ হয়
    if (i == 10) Serial.print(F("[10s]"));
    if (i == 20) Serial.print(F("[20s]"));
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println((__FlashStringHelper*)STR_WIFI_CONNECTED);
    Serial.print((__FlashStringHelper*)STR_IP_ADDRESS);
    Serial.println(WiFi.localIP());
    // Static IP conflict check: assigned IP কি আমরা চাওয়া IP?
    if (useStaticIP) {
      IPAddress assigned = WiFi.localIP();
      IPAddress wanted(staticIP_oct[0], staticIP_oct[1], staticIP_oct[2], staticIP_oct[3]);
      if (assigned != wanted) {
        Serial.println(F("Warning: Got different IP than requested (possible conflict)"));
        Serial.print(F("Wanted: ")); Serial.println(wanted);
        Serial.print(F("Got:    ")); Serial.println(assigned);
      }
    }
    wifiRetryCount = 0;

    timeClient.begin();
    delay(1000);
    Serial.println((__FlashStringHelper*)STR_NTP_SYNCING);

    if (validateAndSyncNTP()) {
      DateTime now = rtc.now();
      Serial.print((__FlashStringHelper*)STR_CURRENT_TIME);
      Serial.printf("%04d/%02d/%02d %02d:%02d:%02d\n", now.year(), now.month(), now.day(), now.hour(), now.minute(), now.second());
      ntpSyncDone = true;
      lastNTPUpdate = millis();
      beepBuzzer(2);
    } else {
      Serial.println((__FlashStringHelper*)STR_NTP_FAILED);
      ntpSyncDone = false;
    }
    lastWiFiAttempt = millis();
  } else {
    Serial.println((__FlashStringHelper*)STR_WIFI_FAILED);
    Serial.println(F("Will retry in 30 seconds..."));
    lastWiFiAttempt = millis();
  }
}

// ========== START WEB UI ==========
void startWebUI() {
  WiFi.mode(WIFI_AP_STA);
  WiFi.softAP(ap_ssid, ap_pass);

  server.on(F("/"),                   handleRoot);
  server.on(F("/about"),              handleAbout);
  server.on(F("/sync"),               handleSync);
  server.on(F("/ntpsync"),            handleNTPSync);
  server.on(F("/savesettings"),       handleSaveSettings);
  server.on(F("/savealarm"),          handleSaveAlarm);
  server.on(F("/savebright"),         handleSaveBrightness);
  server.on(F("/savecolor"),          handleSaveColor);
  server.on(F("/savewifi"),           handleSaveWiFi);
  server.on(F("/togglelight"),        handleToggleLight);
  server.on(F("/savedatesettings"),   handleSaveDateSettings);
  server.on(F("/toggledisplay"),      handleToggleDisplay);
  server.on(F("/savedisplayschedule"),handleSaveDisplaySchedule);
  server.on(F("/saveplaylist"),       handleSavePlaylist);
  server.on(F("/toggleplaylist"),     handleTogglePlaylist);
  server.on(F("/scanwifi"),           handleScanWiFi);
  server.on(F("/savetone"),           handleSaveTone);
  server.on(F("/testtone"),           handleTestTone);
  server.on(F("/savetonerange"),      handleSaveToneRange);
  server.on(F("/ota"),                handleOTAPage);
  server.on(F("/settings"),          handleSettings);
  server.on(F("/changepassword"),    handleChangePassword);
  server.on(F("/checkauth"),         handleCheckAuth);  // FIX: route registered

  // OTA updater — /update এ username/password দিয়ে .bin upload করা যাবে
  httpUpdater.setup(&server, "/update", ota_username, ota_password);

  server.begin();
  Serial.println((__FlashStringHelper*)STR_WEBSERVER_STARTED);

  // FIX: server.begin() হওয়ার পর STA connect করা হচ্ছে। connectWiFiClient()-এর
  // ভেতরের wait loop এখন নন-ব্লকিং (নিচে দেখো), তাই এই সময়ও AP mode দিয়ে
  // web UI ব্যবহার করা যাবে।
  if (wifiClientMode && strlen(wifi_ssid) > 0) connectWiFiClient();

  Serial.println(F("\n========================================"));
  Serial.print((__FlashStringHelper*)STR_AP_MODE_SSID);   Serial.println(ap_ssid);
  Serial.print((__FlashStringHelper*)STR_AP_PASSWORD);    Serial.println(ap_pass);
  Serial.print((__FlashStringHelper*)STR_AP_IP);          Serial.println(WiFi.softAPIP());
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print((__FlashStringHelper*)STR_CONNECTED_TO); Serial.println(wifi_ssid);
    Serial.print((__FlashStringHelper*)STR_CLIENT_IP);    Serial.println(WiFi.localIP());
  }
  Serial.println(F("========================================"));
}

// ========== TONE WEB HANDLERS ==========
void handleSaveToneRange() {
  if (!requireAuth()) return;
  hourlyToneRangeEnabled = server.arg(F("en")) == F("1");
  hourlyToneStartHour    = constrain(server.arg(F("sh")).toInt(), 0, 23);
  hourlyToneEndHour      = constrain(server.arg(F("eh")).toInt(), 0, 23);
  EEPROM.write(ADDR_TONE_RANGE_EN,  hourlyToneRangeEnabled ? 1 : 0);
  EEPROM.write(ADDR_TONE_START_HR,  hourlyToneStartHour);
  EEPROM.write(ADDR_TONE_END_HR,    hourlyToneEndHour);
  EEPROM.commit();
  Serial.printf("Tone range saved: %s %02d:00-%02d:00\n",
    hourlyToneRangeEnabled ? "ON" : "OFF", hourlyToneStartHour, hourlyToneEndHour);
  server.send(200, F("text/plain"), F("OK"));
}

void handleSaveTone() {
  if (!requireAuth()) return;
  toneMode  = constrain(server.arg(F("mode")).toInt(), 0, 2);
  toneIndex = constrain(server.arg(F("idx")).toInt(),  0, TOTAL_TONES - 1);
  saveSettings();
  server.send(200, F("text/plain"), F("OK"));
}
String jsonEscape(const String &s) {
  String out;
  out.reserve(s.length() + 8);
  for (size_t i = 0; i < s.length(); i++) {
    char c = s[i];
    if (c == '"' || c == '\\') { out += '\\'; out += c; }
    else if (c == '\n') out += F("\\n");
    else if (c == '\r') out += F("\\r");
    else if ((uint8_t)c < 0x20) { /* skip other control chars */ }
    else out += c;
  }
  return out;
}

String htmlEscape(const String &s) {
  String out;
  out.reserve(s.length() + 8);
  for (size_t i = 0; i < s.length(); i++) {
    char c = s[i];
    if      (c == '&') out += F("&amp;");
    else if (c == '<') out += F("&lt;");
    else if (c == '>') out += F("&gt;");
    else if (c == '"') out += F("&quot;");
    else if (c == '\'') out += F("&#39;");
    else out += c;
  }
  return out;
}

void handleTestTone() {
  if (!requireAuth()) return;
  int idx = constrain(server.arg(F("idx")).toInt(), 0, TOTAL_TONES - 1);
  server.send(200, F("text/plain"), F("OK"));
  const char* pgmPtr = (const char*)pgm_read_ptr(&TONES[idx]);
  playRTTTL(pgmPtr);
}

// ========== WIFI SCAN HANDLER ==========
void handleScanWiFi() {
  if (!requireAuth()) return;
  int n = WiFi.scanNetworks();  // scan করো
  String json = F("[");
  for (int i = 0; i < n; i++) {
    if (i > 0) json += F(",");
    int rssi = WiFi.RSSI(i);
    // Signal strength percentage (rssi -30 = 100%, -90 = 0%)
    int sig = constrain(map(rssi, -90, -30, 0, 100), 0, 100);
    json += F("{\"ssid\":\"") + jsonEscape(WiFi.SSID(i)) + F("\",");
    json += F("\"rssi\":") + String(rssi) + F(",");
    json += F("\"sig\":") + String(sig) + F(",");
    json += F("\"enc\":") + String(WiFi.encryptionType(i) != ENC_TYPE_NONE ? 1 : 0) + F("}");
  }
  json += F("]");
  WiFi.scanDelete();
  server.send(200, F("application/json"), json);
}

// ========== PLAYLIST WEB HANDLERS ==========
void handleTogglePlaylist() {
  if (!requireAuth()) return;
  playlistEnabled = !playlistEnabled;

  if (playlistEnabled && playlistCount > 0) {
    playlistCurrent   = 0;
    playlistStepStart = millis();
    applyPlaylistStep(0);
  } else if (!playlistEnabled) {
    playlistSmoothActive = false;
  }

  savePlaylist();
  server.send(200, F("text/plain"), playlistEnabled ? F("ON") : F("OFF"));
}

void handleSavePlaylist() {
  if (!requireAuth()) return;
  playlistEnabled = server.arg(F("en")) == F("1");
  int cnt = constrain(server.arg(F("cnt")).toInt(), 0, MAX_PLAYLIST_STEPS);
  playlistCount = cnt;
  for (int i = 0; i < cnt; i++) {
    String si = String(i);
    playlistSteps[i].mode       = constrain(server.arg("m"   + si).toInt(), 0, 4);
    playlistSteps[i].colorIndex = constrain(server.arg("ci"  + si).toInt(), 0, TOTAL_COLORS - 1);
    playlistSteps[i].r          = constrain(server.arg("r"   + si).toInt(), 0, 255);
    playlistSteps[i].g          = constrain(server.arg("g"   + si).toInt(), 0, 255);
    playlistSteps[i].b          = constrain(server.arg("b"   + si).toInt(), 0, 255);
    playlistSteps[i].duration   = constrain(server.arg("d"   + si).toInt(), 1, 255);
    playlistSteps[i].speed      = constrain(server.arg("spd" + si).toInt(), 1, 10);  // per-step speed
    // NEW: "Color change sec" — না পাঠালে পুরো duration ধরে নাও (= পুরনো আচরণ)
    int ccArg = server.hasArg("cc" + si) ? server.arg("cc" + si).toInt()
                                          : playlistSteps[i].duration;
    playlistSteps[i].colorChangeSec = constrain(ccArg, 1, (int)playlistSteps[i].duration);
  }
  if (playlistEnabled && playlistCount > 0) {
    playlistCurrent   = 0;
    playlistStepStart = millis();
    applyPlaylistStep(0);
  } else if (!playlistEnabled) {
    playlistSmoothActive = false;
  }
  savePlaylist();
  server.send(200, F("text/plain"), F("OK"));
}

// ========== WEB UI ROOT ==========
void handleRoot() {
  // Password protection
  if (webuiPasswordEnabled) {
    if (!server.authenticate("admin", webui_pass)) {
      return server.requestAuthentication(BASIC_AUTH, "Sultan Clock", "Password প্রয়োজন");
    }
  }

  DateTime now = rtcOK ? rtc.now() : DateTime(2025, 1, 1, 0, 0, 0);
  int h = now.hour(), m = now.minute(), s = now.second();
  String ampm = "";
  if (hourFormat == 1) {
    ampm = (h >= 12) ? F(" PM") : F(" AM");
    h = h % 12; if (h == 0) h = 12;
  }

  String html = F("<!DOCTYPE html><html><head>");
  html += F("<meta name='viewport' content='width=device-width,initial-scale=1'>");
  html += F("<meta charset='UTF-8'>");
  html += F("<title>Sultan Digital Clock</title>");
  html += F("<link href='https://fonts.googleapis.com/css2?family=Orbitron:wght@400;700;900&family=Rajdhani:wght@300;400;600&display=swap' rel='stylesheet'>");
  html += F("<style>");
  html += F(":root{--gold:#f0c040;--gold2:#ffd97d;--dark:#0a0a0f;--card:#12121c;--card2:#1a1a2e;--accent:#00e5ff;--green:#00ff99;--red:#ff4466;--text:#e8e8f0;--sub:#888aaa}");
  html += F("*{box-sizing:border-box;margin:0;padding:0}");
  html += F("body{font-family:'Rajdhani',sans-serif;background:var(--dark);color:var(--text);min-height:100vh;padding:16px;");
  html += F("background-image:radial-gradient(ellipse at 20% 20%,rgba(240,192,64,0.06) 0%,transparent 50%),radial-gradient(ellipse at 80% 80%,rgba(0,229,255,0.05) 0%,transparent 50%)}");
  html += F(".container{max-width:580px;margin:0 auto}");
  html += F(".header{text-align:center;padding:28px 20px 20px;position:relative}");
  html += F(".header::before{content:'';position:absolute;top:0;left:50%;transform:translateX(-50%);width:80%;height:1px;background:linear-gradient(90deg,transparent,var(--gold),transparent)}");
  html += F(".logo{font-family:'Orbitron',monospace;font-size:11px;letter-spacing:6px;color:var(--gold);text-transform:uppercase;margin-bottom:6px;opacity:0.8}");
  html += F(".title{font-family:'Orbitron',monospace;font-size:22px;font-weight:900;background:linear-gradient(135deg,var(--gold),var(--gold2),var(--gold));-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;letter-spacing:2px}");
  html += F(".subtitle{font-size:12px;color:var(--sub);letter-spacing:3px;margin-top:4px}");
  // ── PREMIUM 3D TIME CARD ──
  html += F(".tilt-wrap{perspective:1200px;margin:16px 0}");
  html += F(".time-box{background:linear-gradient(160deg,#161622,#0d0d15);border:1px solid rgba(240,192,64,0.25);border-radius:22px;padding:26px 20px;margin:0;text-align:center;position:relative;overflow:hidden;");
  html += F("transform-style:preserve-3d;transition:transform 0.15s cubic-bezier(.22,.9,.35,1);will-change:transform;");
  html += F("box-shadow:0 25px 60px -20px rgba(0,0,0,0.65),0 0 0 1px rgba(255,255,255,0.04) inset;animation:breathe3d 4.5s ease-in-out infinite}");
  html += F("@keyframes breathe3d{0%,100%{box-shadow:0 25px 60px -20px rgba(0,0,0,0.65),0 0 35px rgba(240,192,64,0.12),0 0 0 1px rgba(255,255,255,0.04) inset}50%{box-shadow:0 30px 70px -15px rgba(0,0,0,0.7),0 0 65px rgba(240,192,64,0.3),0 0 0 1px rgba(255,255,255,0.06) inset}}");
  html += F(".time-box::after{content:'';position:absolute;inset:0;background:radial-gradient(ellipse at 50% 0%,rgba(240,192,64,0.08),transparent 70%);pointer-events:none;z-index:1}");
  html += F(".glow-ring{position:absolute;inset:-40%;background:conic-gradient(from 0deg,var(--gold),var(--accent),var(--green),var(--gold2),var(--gold));opacity:0.18;filter:blur(30px);animation:spinring 8s linear infinite;pointer-events:none;z-index:0}");
  html += F("@keyframes spinring{to{transform:rotate(360deg)}}");
  html += F(".shine{position:absolute;top:-70%;left:-70%;width:240%;height:240%;background:linear-gradient(115deg,transparent 42%,rgba(255,255,255,0.10) 49%,rgba(255,255,255,0.02) 54%,transparent 62%);pointer-events:none;z-index:1;animation:shinesweep 6s ease-in-out infinite}");
  html += F("@keyframes shinesweep{0%,100%{transform:translate(-6%,-6%)}50%{transform:translate(6%,6%)}}");
  html += F(".particle{position:absolute;bottom:-10px;width:3px;height:3px;border-radius:50%;background:var(--gold2);box-shadow:0 0 6px 1px rgba(240,192,64,0.7);opacity:0;pointer-events:none;z-index:1;animation-name:rise3d;animation-timing-function:ease-in;animation-iteration-count:infinite}");
  html += F("@keyframes rise3d{0%{transform:translateY(0) scale(1);opacity:0}12%{opacity:0.8}88%{opacity:0.25}100%{transform:translateY(-170px) scale(0.3);opacity:0}}");
  html += F(".content3d{position:relative;z-index:2;transform:translateZ(24px)}");
  html += F(".time-main{font-family:'Orbitron',monospace;font-size:52px;font-weight:700;color:var(--gold);letter-spacing:4px;line-height:1;");
  html += F("text-shadow:0 1px 0 rgba(255,255,255,0.25),0 -1px 0 rgba(0,0,0,0.6),0 0 30px rgba(240,192,64,0.45),0 0 60px rgba(240,192,64,0.2),0 6px 18px rgba(0,0,0,0.5)}");
  html += F(".time-date{font-size:15px;color:var(--sub);margin-top:10px;letter-spacing:2px}");
  html += F(".user-pic-wrap{display:flex;justify-content:center;margin:4px 0 2px}");
  html += F(".user-pic{max-width:120px;max-height:120px;width:auto;height:auto;border-radius:16px;border:1px solid rgba(240,192,64,0.3);box-shadow:0 8px 24px rgba(0,0,0,0.5),0 0 24px rgba(240,192,64,0.15);object-fit:cover}");
  html += F(".date-badges{display:flex;gap:8px;justify-content:center;margin-top:12px;flex-wrap:wrap}");
  html += F(".badge{background:rgba(240,192,64,0.1);border:1px solid rgba(240,192,64,0.25);border-radius:8px;padding:5px 12px;font-size:12px;color:var(--gold2);letter-spacing:1px}");
  html += F(".card{background:var(--card);border:1px solid rgba(255,255,255,0.07);border-radius:18px;padding:20px;margin:12px 0;position:relative;overflow:hidden}");
  html += F(".card::before{content:'';position:absolute;top:0;left:0;right:0;height:2px;background:linear-gradient(90deg,transparent,var(--gold),transparent);opacity:0.4}");
  html += F(".card-title{font-family:'Orbitron',monospace;font-size:13px;font-weight:700;color:var(--gold);letter-spacing:2px;text-transform:uppercase;margin-bottom:16px;display:flex;align-items:center;gap:8px}");
  html += F(".card-title .icon{font-size:16px}");
  html += F("label{display:block;margin:10px 0;font-size:14px;color:var(--text);font-weight:600;letter-spacing:0.5px}");
  html += F("input[type=number],input[type=time],input[type=text],input[type=password],select{width:100%;padding:11px 14px;border:1px solid rgba(255,255,255,0.1);border-radius:10px;background:rgba(255,255,255,0.05);color:var(--text);font-family:'Rajdhani',sans-serif;font-size:14px;font-weight:600;transition:border 0.2s;outline:none}");
  html += F("input:focus,select:focus{border-color:var(--gold);box-shadow:0 0 0 2px rgba(240,192,64,0.15)}");
  html += F("select option{background:#1a1a2e;color:var(--text)}");
  html += F("input[type=checkbox],input[type=radio]{width:16px;height:16px;margin-right:8px;accent-color:var(--gold);cursor:pointer;vertical-align:middle}");
  html += F(".btn,.btn-red,.btn-green,.btn-blue{width:100%;padding:13px;border:none;border-radius:12px;font-family:'Orbitron',monospace;font-size:12px;font-weight:700;letter-spacing:2px;cursor:pointer;margin-top:12px;transition:all 0.25s;text-transform:uppercase}");
  html += F(".btn{background:linear-gradient(135deg,#b8860b,var(--gold));color:#000;box-shadow:0 4px 20px rgba(240,192,64,0.25)}");
  html += F(".btn:hover{transform:translateY(-2px);box-shadow:0 8px 30px rgba(240,192,64,0.4)}");
  html += F(".btn-red{background:linear-gradient(135deg,#c0392b,var(--red));color:#fff;box-shadow:0 4px 20px rgba(255,68,102,0.2)}");
  html += F(".btn-red:hover{transform:translateY(-2px);box-shadow:0 8px 30px rgba(255,68,102,0.4)}");
  html += F(".btn-green{background:linear-gradient(135deg,#00b36b,var(--green));color:#000;box-shadow:0 4px 20px rgba(0,255,153,0.2)}");
  html += F(".btn-green:hover{transform:translateY(-2px);box-shadow:0 8px 30px rgba(0,255,153,0.4)}");
  html += F(".btn-blue{background:linear-gradient(135deg,#0099bb,var(--accent));color:#000;box-shadow:0 4px 20px rgba(0,229,255,0.2)}");
  html += F(".btn-blue:hover{transform:translateY(-2px);box-shadow:0 8px 30px rgba(0,229,255,0.4)}");
  html += F(".btn-sm{padding:10px;font-size:11px;margin-top:8px}");
  html += F(".status-row{display:flex;align-items:center;justify-content:space-between;background:rgba(255,255,255,0.04);border-radius:10px;padding:12px 16px;margin:10px 0;border:1px solid rgba(255,255,255,0.06)}");
  html += F(".status-label{font-size:14px;font-weight:600;color:var(--sub)}");
  html += F(".status-on{color:var(--green);font-family:'Orbitron',monospace;font-size:12px;font-weight:700;letter-spacing:2px}");
  html += F(".status-off{color:var(--red);font-family:'Orbitron',monospace;font-size:12px;font-weight:700;letter-spacing:2px}");
  html += F(".range-wrap{display:flex;align-items:center;gap:12px;margin:8px 0}");
  html += F(".range-wrap input[type=range]{flex:1;accent-color:var(--gold);height:5px}");
  html += F(".range-val{background:rgba(240,192,64,0.15);border:1px solid rgba(240,192,64,0.3);color:var(--gold);font-family:'Orbitron',monospace;font-size:12px;padding:4px 10px;border-radius:6px;min-width:48px;text-align:center}");
  html += F(".wifi-ok{background:rgba(0,255,153,0.08);border:1px solid rgba(0,255,153,0.25);border-radius:10px;padding:10px 14px;margin-top:10px;font-size:13px;color:var(--green)}");
  html += F(".wifi-fail{background:rgba(255,68,102,0.08);border:1px solid rgba(255,68,102,0.25);border-radius:10px;padding:10px 14px;margin-top:10px;font-size:13px;color:var(--red)}");
  html += F(".divider{border:none;border-top:1px solid rgba(255,255,255,0.07);margin:16px 0}");
  html += F(".rgb-preview{height:44px;border-radius:10px;margin-top:10px;border:1px solid rgba(255,255,255,0.1);transition:background 0.3s}");
  html += F(".section-kicker{font-size:10px;color:var(--sub);letter-spacing:3px;text-transform:uppercase;margin-bottom:7px}.live-pill{display:inline-flex;align-items:center;gap:7px;padding:5px 9px;border-radius:999px;background:rgba(0,255,153,.08);border:1px solid rgba(0,255,153,.2);color:var(--green);font-size:10px;font-weight:700;letter-spacing:1px}.live-dot{width:6px;height:6px;border-radius:50%;background:var(--green);box-shadow:0 0 9px var(--green);animation:pulseDot 1.5s infinite}@keyframes pulseDot{50%{opacity:.35;transform:scale(.7)}}");
  html += F(".color-hero{border:1px solid rgba(255,255,255,.09);border-radius:16px;padding:14px;margin:0 0 14px;background:radial-gradient(circle at 85% 15%,rgba(0,229,255,.10),transparent 35%),rgba(255,255,255,.025);position:relative;overflow:hidden}.color-preview{height:72px;border-radius:13px;background:linear-gradient(135deg,#ff3b30,#ffd60a,#00e5ff);box-shadow:0 0 35px rgba(240,192,64,.18),inset 0 1px 0 rgba(255,255,255,.22);transition:background .25s,box-shadow .25s}.preview-meta{display:flex;justify-content:space-between;align-items:center;margin-top:10px;gap:10px}.hex-pill{font-family:monospace;font-size:12px;color:var(--text);padding:6px 9px;border-radius:8px;background:rgba(0,0,0,.24);border:1px solid rgba(255,255,255,.08)}.mode-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin:10px 0}.mode-chip{border:1px solid rgba(255,255,255,.08);border-radius:12px;padding:10px;background:rgba(255,255,255,.035);cursor:pointer;transition:.2s;text-align:left}.mode-chip:hover{border-color:rgba(240,192,64,.45);transform:translateY(-1px)}.mode-chip.active{border-color:var(--gold);background:rgba(240,192,64,.10);box-shadow:0 0 18px rgba(240,192,64,.10)}.mode-chip b{display:block;font-size:11px;color:var(--text);letter-spacing:.6px}.mode-chip span{display:block;font-size:10px;color:var(--sub);margin-top:3px}.palette{display:grid;grid-template-columns:repeat(6,1fr);gap:7px;margin-top:8px}.swatch{height:31px;border-radius:9px;border:2px solid transparent;cursor:pointer;box-shadow:inset 0 1px 0 rgba(255,255,255,.25),0 4px 10px rgba(0,0,0,.18);transition:.18s}.swatch:hover{transform:scale(1.06)}.swatch.selected{border-color:#fff;box-shadow:0 0 0 2px var(--gold),0 0 16px rgba(240,192,64,.25)}.save-state{font-size:10px;color:var(--sub);text-align:center;margin-top:7px;min-height:14px}.toast{position:fixed;left:50%;bottom:22px;transform:translate(-50%,18px);opacity:0;pointer-events:none;background:rgba(14,14,24,.94);border:1px solid rgba(240,192,64,.35);color:var(--text);padding:11px 16px;border-radius:12px;box-shadow:0 14px 35px rgba(0,0,0,.45);z-index:99;font-size:12px;transition:.25s}.toast.show{opacity:1;transform:translate(-50%,0)}");
  html += F(".playlist-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.playlist-meter{height:7px;border-radius:99px;background:rgba(255,255,255,.07);overflow:hidden;margin:10px 0}.playlist-meter span{display:block;height:100%;width:0;background:linear-gradient(90deg,var(--gold),var(--accent),var(--green));border-radius:inherit;transition:width .35s}.step-card{background:linear-gradient(145deg,rgba(255,255,255,.065),rgba(255,255,255,.025));border:1px solid rgba(255,255,255,.08);border-radius:15px;padding:13px;margin:9px 0;position:relative;overflow:hidden}.step-card::after{content:'';position:absolute;inset:auto 0 0 0;height:2px;background:var(--step-color,#f0c040);opacity:.7}.step-top{display:flex;align-items:center;gap:10px}.step-no{width:31px;height:31px;border-radius:10px;display:grid;place-items:center;background:rgba(240,192,64,.13);border:1px solid rgba(240,192,64,.28);color:var(--gold);font-family:'Orbitron',monospace;font-size:11px}.step-swatch{width:34px;height:34px;border-radius:10px;background:#ff0000;box-shadow:inset 0 1px 0 rgba(255,255,255,.3),0 0 14px rgba(255,255,255,.08)}.step-title{flex:1}.step-title b{font-size:12px;letter-spacing:1px}.step-title span{display:block;font-size:10px;color:var(--sub);margin-top:2px}.step-mini{font-size:10px;color:var(--sub);padding:5px 7px;border:1px solid rgba(255,255,255,.07);border-radius:7px}.quick-presets{display:flex;gap:6px;flex-wrap:wrap;margin:8px 0}.quick-presets button{border:1px solid rgba(255,255,255,.09);background:rgba(255,255,255,.04);color:var(--sub);border-radius:8px;padding:6px 8px;font-size:9px;cursor:pointer}.quick-presets button:hover{color:var(--gold);border-color:rgba(240,192,64,.4)}.playlist-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}.playlist-actions .btn{margin-top:8px}.hint2{font-size:11px;color:var(--sub);line-height:1.5}@media(max-width:420px){.palette{grid-template-columns:repeat(4,1fr)}.time-main{font-size:43px}.card{padding:16px}}@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation-duration:.01ms!important;transition:none!important}}");
  html += F(".footer{text-align:center;padding:24px 0 8px;border-top:1px solid rgba(240,192,64,0.15);margin-top:16px}");
  html += F(".marquee-wrap{overflow:hidden;margin:8px 0}.marquee{display:inline-block;white-space:nowrap;animation:marquee 18s linear infinite;font-family:'Orbitron',monospace;font-size:11px;color:var(--gold);letter-spacing:3px}");
  html += F("@keyframes marquee{0%{transform:translateX(100vw)}100%{transform:translateX(-100%)}}");
  html += F(".dev-info{font-size:13px;color:var(--sub);line-height:1.8;margin-top:10px}");
  html += F(".dev-info a{color:var(--accent);text-decoration:none}");
  html += F(".hint{font-size:12px;color:var(--sub);margin-top:4px;line-height:1.5}");
  // Sweep badge style
  html += F(".sweep-badge{display:inline-block;background:linear-gradient(90deg,#ff0080,#00e5ff,#00ff88,#ff0080);background-size:200% auto;border-radius:6px;padding:2px 10px;font-size:11px;font-weight:700;color:#000;animation:sweepbadge 2s linear infinite;letter-spacing:1px}");
  html += F("@keyframes sweepbadge{0%{background-position:0%}100%{background-position:200%}}");
  html += F("</style></head><body>");

  // HEADER
  html += F("<div class='container'>");
  html += F("<div class='header'>");
  html += F("<div class='logo'>MD: Sultan Mahamud</div>");
  html += F("<div class='title'>SULTAN DIGITAL CLOCK</div>");
  html += F("<div class='subtitle'>TIME IS VERY IMPORTANT IN OUR LIFE</div>");
  html += F("</div>");

  // USER STICKER/PHOTO — time card এর উপরে (WiFi/internet লাগবে, link fail হলে নিজেই hide হয়ে যাবে)
  html += F("<div class='user-pic-wrap'><img src='https://i.imgur.com/8rsJIoz.png' class='user-pic' alt='' onerror=\"this.parentElement.style.display='none'\"></div>");

  // TIME BOX — Premium 3D glass card (tilt-responsive, glow ring, shine sweep, floating particles)
  html += F("<div class='tilt-wrap'><div class='time-box' id='timebox3d'>");
  html += F("<div class='glow-ring'></div>");
  html += F("<div class='shine'></div>");
  html += F("<div class='particle' style='left:14%;animation-duration:7s;animation-delay:0s'></div>");
  html += F("<div class='particle' style='left:38%;animation-duration:9s;animation-delay:1.5s'></div>");
  html += F("<div class='particle' style='left:62%;animation-duration:6.5s;animation-delay:3s'></div>");
  html += F("<div class='particle' style='left:85%;animation-duration:8s;animation-delay:2s'></div>");
  html += F("<div class='content3d'>");
  html += F("<div class='time-main'>");
  html += (h<10?F("0"):F("")) + String(h) + F(":") + (m<10?F("0"):F("")) + String(m) + F(":") + (s<10?F("0"):F("")) + String(s);
  if (hourFormat == 1) html += F("<span style='font-size:20px;margin-left:8px'>") + ampm + F("</span>");
  html += F("</div>");
  // FIX 5: rtcOK false হলে dummy date (2025-01-01) দেখানো ঠিক না
  // তাই rtcOK check করে date badge দেখাই, না হলে RTC error দেখাই
  if (rtcOK) {
    html += F("<div class='time-date'>") + String(now.day()) + F(" / ") + String(now.month()) + F(" / ") + String(now.year()) + F("</div>");
    BanglaDate bd = getBanglaDate(now);
    html += F("<div class='date-badges'>");
    html += F("<div class='badge'>বাংলা: ") + String(bd.day) + F("/") + String(bd.month) + F("/") + String(bd.year) + F("</div>");
    html += F("</div>");
  } else {
    html += F("<div class='time-date' style='color:#ff4466'>⚠️ RTC সংযুক্ত নেই</div>");
    html += F("<div class='date-badges'><div class='badge' style='border-color:rgba(255,68,102,0.4);color:#ff8888'>Date unavailable</div></div>");
  }
  html += F("</div>"); // .content3d
  html += F("</div></div>"); // .time-box, .tilt-wrap

  server.setContentLength(CONTENT_LENGTH_UNKNOWN);
  server.send(200, F("text/html"), html);
  html = "";

  // ── DISPLAY CONTROL CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>🖥️</span>Display Control</div>");
  html += F("<div class='status-row'><span class='status-label'>Display Status</span>");
  html += F("<span id='displaystatus' class='") + String(displayOn ? F("status-on'>ON") : F("status-off'>OFF")) + F("</span></div>");
  html += F("<button id='displaybtn' onclick='toggleDisplay()' class='") + String(displayOn ? F("btn-red btn'>TURN OFF") : F("btn-green btn'>TURN ON")) + F("</button>");
  html += F("<hr class='divider'>");
  html += F("<div style='font-size:13px;font-weight:700;color:var(--gold);letter-spacing:1px;margin-bottom:10px'>⏰ AUTO SCHEDULE</div>");
  html += F("<p class='hint'>অলসতা মুমিনের জন্য নয়,সময়কে কাজে লাগানোই ইমানের পরিচয়।</p>");
  html += F("<label><input type='checkbox' id='autosch' "); if (autoDisplaySchedule) html += F("checked");
  html += F("> Enable Auto Schedule</label>");
  char offTime[6], onTime[6];
  snprintf(offTime, sizeof(offTime), "%02d:%02d", displayOffHour, displayOffMinute);
  snprintf(onTime,  sizeof(onTime),  "%02d:%02d", displayOnHour,  displayOnMinute);
  html += F("<label>Display OFF Time:<input type='time' id='offtime' value='") + String(offTime) + F("'></label>");
  html += F("<label>Display ON Time:<input type='time' id='ontime' value='")   + String(onTime)  + F("'></label>");
  html += F("<button onclick='saveDisplaySchedule()' class='btn btn-sm'>SAVE SCHEDULE</button></div>");
  server.sendContent(html); html = "";

  // ── LIGHT CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>💡</span>D7 Light Control</div>");
  html += F("<div class='status-row'><span class='status-label'>Light Status</span>");
  html += F("<span id='lightstatus' class='") + String(lightState ? F("status-on'>ON") : F("status-off'>OFF")) + F("</span></div>");
  html += F("<button id='lightbtn' onclick='toggleLight()' class='") + String(lightState ? F("btn-red btn'>TURN OFF") : F("btn-green btn'>TURN ON")) + F("</button></div>");
  server.sendContent(html); html = "";

  // ── TIME SYNC CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>🕐</span>Time Sync</div>");
  html += F("<button onclick='syncPhone()' class='btn btn-sm'>📱 SYNC FROM PHONE</button>");
  if (WiFi.status() == WL_CONNECTED)
    html += F("<button onclick='syncNTP()' class='btn-blue btn btn-sm'>🌐 SYNC FROM NTP (UTC+6 BD)</button>");
  html += F("</div>");
  server.sendContent(html); html = "";

  // ── DATE SETTINGS CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>📅</span>Smart Date Display</div>");
  html += F("<p class='hint' style='margin-bottom:12px'>সততাই মানুষের সবচেয়ে বড় শক্তি।</p>");
  html += F("<label><input type='checkbox' id='showEnglishDate' "); if (enableEnglishDate) html += F("checked"); html += F("> English Date</label>");
  html += F("<label><input type='checkbox' id='showBanglaDate' ");  if (enableBanglaDate)  html += F("checked"); html += F("> বাংলা তারিখ</label>");
  html += F("<button onclick='saveDateSettings()' class='btn btn-sm'>SAVE DATE SETTINGS</button></div>");
  server.sendContent(html); html = "";

  // ── HOURLY TONE CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>🎵</span>Hourly Tone</div>");
  html += F("<p class='hint' style='margin-bottom:12px'>মানুষের সাথে অহংকার করে কথা বলো না,আল্লাহ অহংকারীকে পছন্দ করেন না।</p>");

  // Enable checkbox
  html += F("<label><input type='checkbox' id='hourlybeep2' ");
  if (hourlyBeepEnabled) html += F("checked");
  html += F("> Hourly Tone চালু রাখো</label>");

  html += F("<hr class='divider'>");

  // ── TONE TIME RANGE ──
  html += F("<div style='font-size:13px;font-weight:700;color:var(--gold);margin-bottom:8px'>🌙 Active Time Range</div>");
  html += F("<p class='hint' style='margin-bottom:10px'>ধৈর্য ধারণ করো বিপদের সময়ে,নিশ্চয়ই আল্লাহ ধৈর্যশীলদের ভালোবাসেন।।</p>");
  html += F("<label><input type='checkbox' id='tonerangeen' ");
  if (hourlyToneRangeEnabled) html += F("checked");
  html += F(" onchange='toggleToneRange()'> Time Range চালু করো</label>");
  html += F("<div id='tonerangediv' style='display:");
  html += hourlyToneRangeEnabled ? F("block") : F("none");
  html += F(";margin-top:10px;background:rgba(255,255,255,0.04);border-radius:10px;padding:12px;border:1px solid rgba(255,255,255,0.08)'>");
  {
    char sh[6], eh[6];
    snprintf(sh, sizeof(sh), "%02d:00", hourlyToneStartHour);
    snprintf(eh, sizeof(eh), "%02d:00", hourlyToneEndHour);
    html += F("<label>Tone শুরুর সময়:<input type='time' id='tonestarthr' value='") + String(sh) + F("'></label>");
    html += F("<label>Tone বন্ধের সময়:<input type='time' id='toneendhr' value='") + String(eh) + F("'></label>");
    html += F("<p class='hint' style='margin-top:6px'>সত্য কথা বলতে ভয় পেও না,কারণ সত্যই মানুষকে মুক্তি দেয়।</p>");
  }
  html += F("</div>");
  html += F("<button onclick='saveToneRange()' class='btn-blue btn btn-sm'>💾 SAVE TIME RANGE</button>");

  html += F("<hr class='divider'>");

  // Tone Mode
  html += F("<div style='font-size:13px;font-weight:700;color:var(--gold);margin-bottom:8px'>🎲 Tone Mode</div>");
  html += F("<label><input type='radio' name='tmode' value='1' ");
  if (toneMode==1) html += F("checked");
  html += F(" onchange='toneModeChange()'> 🎲 Random — প্রতি ঘণ্টায় এলোমেলো tone</label>");
  html += F("<label><input type='radio' name='tmode' value='2' ");
  if (toneMode==2) html += F("checked");
  html += F(" onchange='toneModeChange()'> 🔢 Sequential — ১→২→...→১০→১ ক্রমে</label>");
  html += F("<label><input type='radio' name='tmode' value='0' ");
  if (toneMode==0) html += F("checked");
  html += F(" onchange='toneModeChange()'> 📌 Fixed — সবসময় একটাই tone</label>");

  // Fixed tone selector
  html += F("<div id='fixedtonediv' style='display:");
  html += (toneMode==0) ? F("block") : F("none");
  html += F(";margin-top:8px'>");
  html += F("<label>Tone নির্বাচন করো:<select id='toneidx'>");

  const char* toneNames[] = {
    "🎵 Smooth Criminal",
    "🎵 Gorilla Clint",
    "🎵 Place 4 My Head",
    "🎵 Mambo Italiano",
    "🎵 Bad Boys",
    "🎵 NSync Pop",
    "🎵 Groovy Blue",
    "🔔 Jingle Bells",
    "🔔 Nokia Ring",
    "🎶 Retro Groove",
    "🎮 Doom Level 1",
    "🎮 Contra",
    "🎮 Mario Real"
  };
  for (int i = 0; i < TOTAL_TONES; i++) {
    html += F("<option value='") + String(i) + F("'");
    if (i == toneIndex) html += F(" selected");
    html += F(">") + String(toneNames[i]) + F("</option>");
  }
  html += F("</select></label></div>");

  // Test buttons
  html += F("<hr class='divider'>");
  html += F("<div style='font-size:13px;font-weight:700;color:var(--gold);margin-bottom:8px'>🔊 Test Tones</div>");
  html += F("<p class='hint' style='margin-bottom:8px'>জ্ঞান অর্জন করো এবং তা কাজে লাগাও,জ্ঞান ছাড়া জীবন অন্ধকার।</p>");
  html += F("<div style='display:grid;grid-template-columns:1fr 1fr;gap:6px'>");
  for (int i = 0; i < TOTAL_TONES; i++) {
    html += F("<button onclick='testTone(") + String(i) + F(",this)' class='btn btn-sm' style='font-size:10px;padding:8px'>") + String(toneNames[i]) + F("</button>");
  }
  html += F("</div>");
  html += F("<button onclick='saveTone()' class='btn btn-sm' style='margin-top:12px'>💾 SAVE TONE SETTINGS</button>");
  html += F("</div>");
  server.sendContent(html); html = "";
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>📶</span>WiFi Settings</div>");

  // Scan button + results area
  html += F("<button onclick='scanWiFi()' class='btn-blue btn btn-sm' id='scanbtn'>🔍 SCAN WiFi NETWORKS</button>");
  html += F("<div id='scanresult' style='margin-top:10px'></div>");
  html += F("<hr class='divider'>");

  html += F("<label><input type='checkbox' id='wifienable' "); if (wifiClientMode) html += F("checked"); html += F("> Enable WiFi Connection</label>");
  html += F("<label>WiFi Name (SSID):<input type='text' id='wifissid' value='") + htmlEscape(String(wifi_ssid)) + F("' placeholder='নেটওয়ার্ক নাম লিখুন বা উপরে scan করুন'></label>");
  html += F("<label><input type='checkbox' id='wifihaspass' "); if (wifiHasPassword) html += F("checked"); html += F(" onchange='togglePassword()'> WiFi Has Password</label>");
  html += F("<div id='passdiv' style='display:") + String(wifiHasPassword ? F("block") : F("none")) + F("'>");
  html += F("<label>Password:<input type='password' id='wifipass' value='") + String(wifi_pass) + F("'></label></div>");
  html += F("<hr class='divider'>");
  html += F("<div style='font-size:13px;font-weight:700;color:var(--gold);letter-spacing:1px;margin-bottom:10px'>🔒 STATIC IP</div>");
  html += F("<label><input type='checkbox' id='staticen' "); if (useStaticIP) html += F("checked"); html += F(" onchange='toggleStaticIP()'> Use Static IP</label>");
  html += F("<div id='staticdiv' style='display:") + String(useStaticIP ? F("block") : F("none")) + F(";margin-top:8px'>");
  html += F("<label>Static IP:<input type='text' id='sip' value='") + String(staticIP_oct[0])+F(".")+String(staticIP_oct[1])+F(".")+String(staticIP_oct[2])+F(".")+String(staticIP_oct[3]) + F("'></label>");
  html += F("<label>Gateway:<input type='text' id='gip' value='") + String(gatewayIP_oct[0])+F(".")+String(gatewayIP_oct[1])+F(".")+String(gatewayIP_oct[2])+F(".")+String(gatewayIP_oct[3]) + F("'></label>");
  html += F("<label>Subnet:<input type='text' id='snip' value='") + String(subnetIP_oct[0])+F(".")+String(subnetIP_oct[1])+F(".")+String(subnetIP_oct[2])+F(".")+String(subnetIP_oct[3]) + F("'></label>");
  html += F("<p class='hint'>⚠️ Gateway = Router IP (যেমন: 192.168.0.1)</p></div>");
  html += F("<button onclick='saveWiFi()' class='btn btn-sm'>SAVE &amp; RESTART</button>");
  if (WiFi.status() == WL_CONNECTED) {
    html += F("<div class='wifi-ok'>✅ Connected: <strong>") + htmlEscape(String(wifi_ssid)) + F("</strong> &nbsp;|&nbsp; IP: ") + WiFi.localIP().toString() + F("</div>");
  } else {
    html += F("<div class='wifi-fail'>⚠️ Not connected to home WiFi</div>");
  }
  html += F("</div>");
  server.sendContent(html); html = "";

  // ── DISPLAY SETTINGS CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>⚙️</span>Display Settings</div>");
  html += F("<label><input type='radio' name='fmt' id='fmt24' "); if (hourFormat==0) html += F("checked"); html += F("> 24-Hour</label>");
  html += F("<label><input type='radio' name='fmt' id='fmt12' "); if (hourFormat==1) html += F("checked"); html += F("> 12-Hour</label>");
  html += F("<label><input type='checkbox' id='colonblink' ");    if (colonBlink)    html += F("checked"); html += F("> Colon Blink</label>");
  html += F("<label><input type='checkbox' id='showdate' ");      if (showDateEnabled) html += F("checked"); html += F("> Show Date</label>");
  html += F("<p class='hint' style='margin-top:6px'>পিতা-মাতার সাথে সদ্ব্যবহার করো,তাদের কষ্টের কথা স্মরণ রাখো।</p>");
  html += F("<button onclick='saveSettings()' class='btn btn-sm'>SAVE DISPLAY</button></div>");
  server.sendContent(html); html = "";

  // ── ALARMS CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>⏰</span>Alarms</div>");
  html += F("<p class='hint' style='margin-bottom:12px'>শিরক করো না কখনো আল্লাহর সাথে,এটি সবচেয়ে বড় জুলুম।</p>");
  {
    const char* alarmToneNames[] = {
      "Smooth Criminal","Gorilla Clint","Place 4 My Head","Mambo Italiano",
      "Bad Boys","NSync Pop","Groovy Blue","Jingle Bells",
      "Nokia Ring","Retro Groove","Doom Level 1","Contra","Mario Real"
    };
    for (int i = 0; i < MAX_ALARMS; i++) {
      char ts[6]; snprintf(ts, sizeof(ts), "%02d:%02d", alarms[i].hour, alarms[i].minute);
      html += F("<div style='background:rgba(255,255,255,0.04);border-radius:12px;padding:14px;margin:10px 0;border:1px solid rgba(255,255,255,0.08)'>");
      html += F("<div style='font-size:12px;font-weight:700;color:var(--gold);margin-bottom:10px;letter-spacing:1px'>⏰ ALARM ") + String(i+1) + F("</div>");
      html += F("<div style='display:flex;align-items:center;gap:10px;flex-wrap:wrap'>");
      html += F("<input type='time' id='alarm") + String(i) + F("' value='") + String(ts) + F("' style='flex:1;min-width:120px;padding:8px 10px'>");
      html += F("<label style='margin:0;display:flex;align-items:center;gap:6px;font-size:13px'><input type='checkbox' id='en") + String(i) + F("'");
      if (alarms[i].enabled) html += F(" checked");
      html += F("> চালু</label></div>");
      html += F("<label style='margin-top:10px;font-size:13px'>🎵 Ringtone:<select id='atone") + String(i) + F("'>");
      for (int t = 0; t < TOTAL_TONES; t++) {
        html += F("<option value='") + String(t) + F("'");
        if (t == alarms[i].toneIndex) html += F(" selected");
        html += F(">") + String(alarmToneNames[t]) + F("</option>");
      }
      html += F("</select></label>");
      html += F("<button onclick='testToneBtn(") + String(i) + F(",this)' class='btn-blue btn btn-sm' style='margin-top:8px'>&#9654; Test This Alarm Tone</button>");
      html += F("</div>");
    }
  }
  html += F("<button onclick='saveAlarms()' class='btn btn-sm'>💾 SAVE ALARMS</button></div>");
  server.sendContent(html); html = "";

  // ── BRIGHTNESS CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>🔆</span>Brightness</div>");
  html += F("<label><input type='checkbox' id='autoldr' "); if (autoLDR) html += F("checked"); html += F("> Auto Brightness (LDR Sensor)</label>");
  html += F("<p class='hint' style='margin-bottom:8px'>আল্লাহ কাউকে তার সাধ্যের বাইরে কষ্ট দেন না,তাই সব পরিস্থিতিতে ধৈর্য রাখো।</p>");
  html += F("<div class='range-wrap'><span style='font-size:13px;color:var(--sub);min-width:60px'>Manual:</span>");
  html += F("<input type='range' id='bright' min='1' max='255' value='") + String(brightnessLevel) + F("' oninput='updateBrightValue(this.value)'>");
  html += F("<span class='range-val' id='brightval'>") + String(brightnessLevel) + F("</span></div>");
  if (autoLDR) {
    html += F("<div class='wifi-ok' style='margin-top:12px'>Live LDR Raw: <strong>") + String(lastLdrRaw) + F("</strong> / 1023 | Applied Brightness: <strong>") + String(lastLdrBrightness) + F("</strong></div>");
  }
  html += F("<hr class='divider'>");
  html += F("<div style='font-size:13px;font-weight:700;color:var(--gold);letter-spacing:1px;margin-bottom:6px'>Night/Day Calibration</div>");
  html += F("<label>Low Cut (niche/soman hole minimum brightness):<input type='number' id='ldrlowcut' min='0' max='1023' value='") + String(ldrLowCut) + F("'></label>");
  html += F("<label>High Cut (upore/soman hole maximum brightness):<input type='number' id='ldrhighcut' min='0' max='1023' value='") + String(ldrHighCut) + F("'></label>");
  html += F("<p class='hint'>তোমরা হতাশ হয়ো না আল্লাহর রহমত থেকে,&nbsp;|&nbsp;সবসময় তওবা করে ফিরে আসো। </p>");
  html += F("<button onclick='saveBrightness()' class='btn btn-sm'>SAVE BRIGHTNESS</button></div>");
  server.sendContent(html); html = "";

  // ── COLOR CARD (with Sweep Random) ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>🎨</span>Color Studio <span class='live-pill' style='margin-left:auto'><span class='live-dot'></span>LIVE</span></div>");
  html += F("<div class='color-hero'><div class='section-kicker'>SULTAN LIGHT ENGINE</div><div class='color-preview' id='colorpreview'></div><div class='preview-meta'><span class='hint2'>Real-time visual preview • 12 premium colors</span><span class='hex-pill' id='hexpreview'>#FF0000</span></div></div>");
  html += F("<div class='mode-grid'><div class='mode-chip' id='modechip0' onclick='pickMode(0)'><b>◈ STATIC</b><span>Single premium color</span></div><div class='mode-chip' id='modechip1' onclick='pickMode(1)'><b>◌ SMOOTH FADE</b><span>Continuous cross-fade</span></div><div class='mode-chip' id='modechip2' onclick='pickMode(2)'><b>✦ RAINBOW</b><span>Dynamic spectrum</span></div><div class='mode-chip' id='modechip3' onclick='pickMode(3)'><b>RGB CUSTOM</b><span>Precision 0–255</span></div><div class='mode-chip' id='modechip4' onclick='pickMode(4)'><b>✧ SWEEP RANDOM</b><span>Per-pixel motion</span></div></div>");
  html += F("<label>Mode:<select id='colormode' onchange='toggleColorOpts()'>");
  html += F("<option value='0'"); if(colorMode==0) html+=F(" selected"); html+=F(">Static Color</option>");
  html += F("<option value='1'"); if(colorMode==1) html+=F(" selected"); html+=F(">Smooth Fade</option>");
  html += F("<option value='2'"); if(colorMode==2) html+=F(" selected"); html+=F(">Rainbow</option>");
  html += F("<option value='3'"); if(colorMode==3) html+=F(" selected"); html+=F(">Custom RGB</option>");
  html += F("<option value='4'"); if(colorMode==4) html+=F(" selected"); html+=F(">Sweep Random</option>");
  html += F("</select></label>");

  // Static / Smooth Fade options
  html += F("<div id='staticopts' style='display:");
  html += (colorMode==0||colorMode==1) ? F("block") : F("none");
  html += F("'><div class='section-kicker'>PALETTE</div><div class='palette' id='palette'>");
  const uint32_t paletteHex[] = {0xFF0000,0xFFFF00,0xFFA500,0xFFC040,0x00FFFF,0x00FF00,0x00C88C,0x8000FF,0xFF00FF,0x0080FF,0x0028FF,0xFFFFFF};
  const char* colorNames[] = {"Red","Yellow","Orange","Gold","Cyan","Green","Teal","Purple","Magenta","Blue","Deep Blue","White"};
  for (int pi=0; pi<TOTAL_COLORS; pi++) { html += F("<button type='button' class='swatch"); if (pi==staticColorIndex) html += F(" selected"); html += F("' data-i='") + String(pi) + F("' style='background:#"); char hx[7]; sprintf(hx,"%06lX",(unsigned long)paletteHex[pi]); html += String(hx); html += F("' onclick='pickPalette(") + String(pi) + F(")' title='") + String(colorNames[pi]) + F("'></button>"); }
  html += F("</div><label style='margin-top:12px'>Starting Color:<select id='staticcolor' onchange='syncPalette(this.value)'>");
  for (int i=0; i<TOTAL_COLORS; i++) {
    html += F("<option value='") + String(i) + F("'");
    if (i==staticColorIndex) html += F(" selected");
    html += F(">") + String(colorNames[i]) + F("</option>");
  }
  html += F("</select></label>");
  html += F("<label>Color Change Interval (sec):</label>");
  html += F("<p class='hint' style='margin-bottom:6px'>সময় আল্লাহর অমূল্য আমানত, &nbsp;|&nbsp;প্রতিটি মুহূর্তের হিসাব একদিন দিতে হবে।</p>");
  html += F("<input type='number' id='colorint' min='1' max='120' value='") + String(colorChangeInterval) + F("'></div>");

  // Rainbow speed slider
  html += F("<div id='rainbowopts' style='display:");
  html += (colorMode==2) ? F("block") : F("none");
  html += F(";background:rgba(255,255,255,0.04);border-radius:10px;padding:14px;margin-top:10px;border:1px solid rgba(255,255,255,0.1)'>");
  html += F("<div style='font-size:12px;font-weight:700;color:var(--gold);margin-bottom:10px;letter-spacing:1px'>\U0001f308 RAINBOW SPEED</div>");
  html += F("<div class='range-wrap'><span style='font-size:12px;color:var(--sub);min-width:44px'>\u09a6\u09cd\u09b0\u09c1\u09a4</span>");
  html += F("<input type='range' id='animspeed_rb' min='1' max='10' value='") + String(animSpeed) + F("' oninput='document.getElementById(\"speedval_rb\").innerText=this.value'>");
  html += F("<span style='font-size:12px;color:var(--sub);min-width:36px'>\u09a7\u09c0\u09b0</span>");
  html += F("<span class='range-val' id='speedval_rb'>") + String(animSpeed) + F("</span></div>");
  html += F("<p style='font-size:12px;color:var(--sub);margin-top:6px'>1 = \u09b8\u09ac\u099a\u09c7\u09af\u09bc\u09c7 \u09a6\u09cd\u09b0\u09c1\u09a4 &nbsp;|&nbsp; 10 = \u09b8\u09ac\u099a\u09c7\u09af\u09bc\u09c7 \u09a7\u09c0\u09b0</p></div>");

  // Custom RGB options
  html += F("<div id='rgbopts' style='display:");
  html += (colorMode==3) ? F("block") : F("none");
  html += F("'>");
  html += F("<div class='range-wrap'><span style='color:#ff6666;min-width:24px;font-weight:700'>R</span><input type='range' id='customr' min='0' max='255' value='") + String(customR) + F("' oninput='updateRGBPreview()'><span class='range-val' id='rval'>") + String(customR) + F("</span></div>");
  html += F("<div class='range-wrap'><span style='color:#66ff66;min-width:24px;font-weight:700'>G</span><input type='range' id='customg' min='0' max='255' value='") + String(customG) + F("' oninput='updateRGBPreview()'><span class='range-val' id='gval'>") + String(customG) + F("</span></div>");
  html += F("<div class='range-wrap'><span style='color:#6699ff;min-width:24px;font-weight:700'>B</span><input type='range' id='customb' min='0' max='255' value='") + String(customB) + F("' oninput='updateRGBPreview()'><span class='range-val' id='bval'>") + String(customB) + F("</span></div>");
  html += F("<div class='rgb-preview' id='rgbpreview' style='background:rgb(") + String(customR) + F(",") + String(customG) + F(",") + String(customB) + F(")'></div></div>");

  // Sweep Random info panel
  html += F("<div id='sweepopts' style='display:");
  html += (colorMode==4) ? F("block") : F("none");
  html += F(";background:rgba(255,255,255,0.04);border-radius:10px;padding:14px;margin-top:10px;border:1px solid rgba(255,255,255,0.1)'>");
  html += F("<span class='sweep-badge'>SULTAN MAHAMUD</span>");
  html += F("<div style='font-size:12px;font-weight:700;color:var(--gold);margin:12px 0 8px;letter-spacing:1px'>✨ SWEEP SPEED</div>");
  html += F("<div class='range-wrap'><span style='font-size:12px;color:var(--sub);min-width:44px'>দ্রুত</span>");
  html += F("<input type='range' id='animspeed_sw' min='1' max='10' value='") + String(animSpeed) + F("' oninput='document.getElementById(\"speedval_sw\").innerText=this.value'>");
  html += F("<span style='font-size:12px;color:var(--sub);min-width:36px'>ধীর</span>");
  html += F("<span class='range-val' id='speedval_sw'>") + String(animSpeed) + F("</span></div>");
  html += F("<p style='font-size:12px;color:var(--sub);margin-top:6px'>1 = সবচেয়ে দ্রুত &nbsp;|&nbsp; 10 = সবচেয়ে ধীর</p>");
  html += F("<p style='margin-top:10px;font-size:13px;color:var(--sub);line-height:1.6'>");
  html += F("আল্লাহ সবকিছুই দেখেন ও জানেন,তাই প্রতিটি কাজে সতর্ক থাকো।<br>");
  html += F("আল্লাহ অহংকারী ও গর্বিতদের পছন্দ করেন না,তাই বিনয়ী হও ও নম্রভাবে চলাফেরা করো।</p></div>");

  html += F("<button onclick='saveColor()' class='btn btn-sm'>SAVE COLOR</button></div>");
  server.sendContent(html); html = "";

  // ── COLOR PLAYLIST CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'><span class='icon'>🎬</span>Smart Color Playlist <span class='live-pill' style='margin-left:auto'><span class='live-dot'></span>AUTO</span></div>");
  html += F("<div class='playlist-head'><div><div class='section-kicker'>SEQUENCE ENGINE</div><div class='hint2'>Build up to 8 scenes. Each scene keeps its own mode, color, duration and animation speed.</div></div></div><div class='playlist-meter'><span id='playlistmeter'></span></div>");
  html += F("<p class='hint' style='margin-bottom:12px'>মানুষের সাথে সুন্দরভাবে কথা বলো,উত্তম আচরণই মুমিনের পরিচয়।</p>");
  html += F("<div class='status-row'><span class='status-label'>Playlist</span>");
  html += F("<span id='plstatus' class='") + String(playlistEnabled ? F("status-on'>ON") : F("status-off'>OFF")) + F("</span></div>");
  html += F("<button id='plbtn' onclick='togglePlaylist()' class='") + String(playlistEnabled ? F("btn-red btn btn-sm'>DISABLE") : F("btn-green btn btn-sm'>ENABLE")) + F(" PLAYLIST</button>");
  if (playlistEnabled && playlistCount > 0) {
    html += F("<div style='background:rgba(0,255,153,0.08);border:1px solid rgba(0,255,153,0.2);border-radius:8px;padding:8px 12px;margin-top:8px;font-size:13px;color:var(--green)'>▶ Step ") + String(playlistCurrent+1) + F(" / ") + String(playlistCount) + F(" চলছে</div>");
  }
  html += F("<hr class='divider'>");
  html += F("<label>কতটা Step (1-8):<select id='plcnt' onchange='buildSteps();document.getElementById(&quot;playlistmeter&quot;).style.width=(this.value/8*100)+&quot;%&quot;'>");
  for (int i = 1; i <= MAX_PLAYLIST_STEPS; i++) {
    html += F("<option value='") + String(i) + F("'");
    if (i == max(1,(int)playlistCount)) html += F(" selected");
    html += F(">") + String(i) + F(" টা</option>");
  }
  html += F("</select></label>");
  html += F("<div id='plsteps'>");
  int sc = max(1,(int)playlistCount);
  const char* cn[] = {"Red","Yellow","Orange","Gold","Cyan","Green","Teal","Purple","Magenta","Blue","Deep Blue","White"};
  for (int i = 0; i < sc; i++) {
    uint8_t sm=(i<playlistCount)?playlistSteps[i].mode:0;
    uint8_t sci=(i<playlistCount)?playlistSteps[i].colorIndex:0;
    uint8_t sr=(i<playlistCount)?playlistSteps[i].r:255;
    uint8_t sg=(i<playlistCount)?playlistSteps[i].g:0;
    uint8_t sb=(i<playlistCount)?playlistSteps[i].b:0;
    uint8_t sd=(i<playlistCount)?playlistSteps[i].duration:30;
    uint8_t ss=(i<playlistCount)?playlistSteps[i].speed:5;  // per-step speed
    uint8_t scc=(i<playlistCount)?playlistSteps[i].colorChangeSec:sd;  // NEW: color change interval
    html += F("<div class='step-card' id='stepcard") + String(i) + F("' style='--step-color:#f0c040'><div class='step-top'><div class='step-no'>") + String(i+1) + F("</div><div class='step-swatch' id='stepswatch") + String(i) + F("'></div><div class='step-title'><b>SCENE ") + String(i+1) + F("</b><span id='stepmeta") + String(i) + F("'>Configure your light scene</span></div><span class='step-mini' id='stepdur") + String(i) + F("'>") + String(sd) + F("s</span></div>");
    html += F("<div style='font-size:12px;color:var(--gold);font-weight:700;margin-bottom:8px'>STEP ") + String(i+1) + F("</div>");
    html += F("<label>Mode:<select id='m") + String(i) + F("' onchange='stepModeChange(") + String(i) + F(")'>");
    html += F("<option value='0'"); if(sm==0)html+=F(" selected"); html+=F(">Static</option>");
    html += F("<option value='1'"); if(sm==1)html+=F(" selected"); html+=F(">Smooth Fade</option>");
    html += F("<option value='2'"); if(sm==2)html+=F(" selected"); html+=F(">Rainbow</option>");
    html += F("<option value='3'"); if(sm==3)html+=F(" selected"); html+=F(">Custom RGB</option>");
    html += F("<option value='4'"); if(sm==4)html+=F(" selected"); html+=F(">Sweep Random</option>");
    html += F("</select></label>");
    html += F("<div id='sci") + String(i) + F("' style='display:") + ((sm==0||sm==1)?F("block"):F("none")) + F("'><label>Color:<select id='ci") + String(i) + F("'>");
    for (int j=0;j<TOTAL_COLORS;j++){html+=F("<option value='")+String(j)+F("'");if(j==sci)html+=F(" selected");html+=F(">")+String(cn[j])+F("</option>");}
    html += F("</select></label></div>");
    html += F("<div id='srgb") + String(i) + F("' style='display:") + ((sm==3)?F("block"):F("none")) + F("'>");
    html += F("<div class='range-wrap'><span style='color:#ff6666;min-width:20px;font-weight:700'>R</span><input type='range' id='r") + String(i) + F("' min='0' max='255' value='") + String(sr) + F("' oninput='document.getElementById(\"rv") + String(i) + F("\").textContent=this.value'><span class='range-val' id='rv") + String(i) + F("'>") + String(sr) + F("</span></div>");
    html += F("<div class='range-wrap'><span style='color:#66ff66;min-width:20px;font-weight:700'>G</span><input type='range' id='g") + String(i) + F("' min='0' max='255' value='") + String(sg) + F("' oninput='document.getElementById(\"gv") + String(i) + F("\").textContent=this.value'><span class='range-val' id='gv") + String(i) + F("'>") + String(sg) + F("</span></div>");
    html += F("<div class='range-wrap'><span style='color:#6699ff;min-width:20px;font-weight:700'>B</span><input type='range' id='b") + String(i) + F("' min='0' max='255' value='") + String(sb) + F("' oninput='document.getElementById(\"bv") + String(i) + F("\").textContent=this.value'><span class='range-val' id='bv") + String(i) + F("'>") + String(sb) + F("</span></div></div>");
    html += F("<div style='display:flex;gap:10px;flex-wrap:wrap;align-items:flex-end'>");
    html += F("<label>Duration (sec):<input type='number' id='d") + String(i) + F("' min='1' max='255' value='") + String(sd) + F("' style='width:80px;display:inline-block' oninput='if(document.getElementById(\"cc") + String(i) + F("\"))document.getElementById(\"cc") + String(i) + F("\").max=this.value;updateStepVisual(") + String(i) + F(")'></label>");
    html += F("<div id='sccdiv") + String(i) + F("' style='display:") + ((sm==0||sm==1)?F("block"):F("none")) + F("'><label title='এই সেকেন্ড পরপর পরের প্যালেট কালারে বদলাবে'>Color change sec:<input type='number' id='cc") + String(i) + F("' min='1' max='") + String(sd) + F("' value='") + String(scc) + F("' style='width:80px;display:inline-block' oninput='updateStepVisual(") + String(i) + F(")'></label></div>");
    html += F("</div>");
    // Speed slider — Rainbow ও Sweep select হলেই দেখাবে
    html += F("<div id='sspd") + String(i) + F("' style='display:") + ((sm==2||sm==4)?F("block"):F("none")) + F(";background:rgba(255,200,50,0.06);border-radius:8px;padding:10px;margin:6px 0;border:1px solid rgba(240,192,64,0.15)'>");
    html += F("<div style='font-size:11px;font-weight:700;color:var(--gold);margin-bottom:6px'>⚡ ANIMATION SPEED</div>");
    html += F("<div class='range-wrap'><span style='font-size:11px;color:var(--sub);min-width:36px'>দ্রুত</span>");
    html += F("<input type='range' id='spd") + String(i) + F("' min='1' max='10' value='") + String(ss) + F("' oninput='document.getElementById(\"sv") + String(i) + F("\").textContent=this.value'>");
    html += F("<span style='font-size:11px;color:var(--sub);min-width:30px'>ধীর</span>");
    html += F("<span class='range-val' id='sv") + String(i) + F("'>") + String(ss) + F("</span></div>");
    html += F("<p style='font-size:11px;color:var(--sub);margin-top:4px'>1=সবচেয়ে দ্রুত &nbsp;|&nbsp; 10=সবচেয়ে ধীর</p></div>");
    html += F("</div>");
  }
  html += F("</div>");
  html += F("<div class='quick-presets'><button type='button' onclick='playlistPreset(0)'>🔥 ENERGY</button><button type='button' onclick='playlistPreset(1)'>🌊 OCEAN</button><button type='button' onclick='playlistPreset(2)'>🌅 SUNSET</button><button type='button' onclick='playlistPreset(3)'>🌈 SPECTRUM</button></div><div class='playlist-actions'><button onclick='previewPlaylist()' class='btn btn-blue btn-sm'>▶ PREVIEW SEQUENCE</button><button onclick='savePlaylist()' class='btn btn-sm'>💾 SAVE PLAYLIST</button></div><div class='save-state' id='playliststate'>Changes are local until you save.</div></div><div class='toast' id='toast'></div>");
  server.sendContent(html); html = "";
  html += F("<div class='footer'>");
  html += F("<div class='marquee-wrap'><div class='marquee'>✦ ALHAMDULILLAH ✦ ALHAMDULILLAH ✦ ALHAMDULILLAH ✦ ALHAMDULILLAH ✦</div></div>");
  html += F("<div style='display:flex;gap:10px;justify-content:center;margin:14px 0;flex-wrap:wrap'>");
  html += F("<a href='/settings' style='display:inline-flex;align-items:center;gap:6px;padding:10px 20px;background:linear-gradient(135deg,#b8860b,#f0c040);color:#000;border-radius:12px;font-weight:700;font-size:12px;text-decoration:none;letter-spacing:1px'>⚙️ SETTINGS</a>");
  html += F("</div>");
  html += F("<div class='dev-info'><strong style='color:var(--gold)'>MD: SULTAN MAHAMUD</strong><br>");
  html += F("📞 01740-236384 &nbsp;|&nbsp; ✉️ sultanmahamud5497@gmail.com</div></div></div>");
  html += F("<script>");
  server.sendContent(html); html = "";

  server.sendContent(F("function toggleDisplay(){fetch('/toggledisplay').then(r=>r.text()).then(st=>{let e=document.getElementById('displaystatus');let b=document.getElementById('displaybtn');e.textContent=st;e.className=st=='ON'?'status-on':'status-off';b.textContent=st=='ON'?'TURN OFF':'TURN ON';b.className=st=='ON'?'btn-red btn':'btn-green btn';}).catch(()=>alert('Error'));}"));
  server.sendContent(F("function saveDisplaySchedule(){let auto=document.getElementById('autosch').checked?1:0;let offt=document.getElementById('offtime').value.split(':');let ont=document.getElementById('ontime').value.split(':');fetch('/savedisplayschedule?auto='+auto+'&offh='+offt[0]+'&offm='+offt[1]+'&onh='+ont[0]+'&onm='+ont[1]).then(r=>r.ok?alert('Saved!'):alert('Failed')).catch(()=>alert('Error'));}"));
  server.sendContent(F("function toggleLight(){fetch('/togglelight').then(r=>r.text()).then(st=>{let e=document.getElementById('lightstatus');let b=document.getElementById('lightbtn');e.textContent=st;e.className=st=='ON'?'status-on':'status-off';b.textContent=st=='ON'?'TURN OFF':'TURN ON';b.className=st=='ON'?'btn-red btn':'btn-green btn';}).catch(()=>alert('Error'));}"));
  server.sendContent(F("function syncPhone(){let d=new Date();fetch('/sync?y='+d.getFullYear()+'&mo='+(d.getMonth()+1)+'&d='+d.getDate()+'&h='+d.getHours()+'&m='+d.getMinutes()+'&s='+d.getSeconds()).then(r=>r.ok?alert('✅ Time Synced!'):alert('Failed')).catch(()=>alert('Error'));}"));
  server.sendContent(F("function syncNTP(){fetch('/ntpsync').then(r=>r.ok?alert('✅ NTP Sync Done!'):alert('NTP Failed')).catch(()=>alert('Error'));}"));
  server.sendContent(F("function saveSettings(){let f=document.getElementById('fmt12').checked?1:0;let sd=document.getElementById('showdate')?(document.getElementById('showdate').checked?1:0):1;let cb=document.getElementById('colonblink').checked?1:0;let hb=document.getElementById('hourlybeep2').checked?1:0;fetch('/savesettings?f='+f+'&sd='+sd+'&cb='+cb+'&hb='+hb).then(r=>r.ok?alert('Saved!'):alert('Failed'));}"));
  server.sendContent(F("function saveAlarms(){let t0=document.getElementById('alarm0').value.split(':');let e0=document.getElementById('en0').checked?1:0;let a0=document.getElementById('atone0').value;fetch('/savealarm?i=0&h='+t0[0]+'&m='+t0[1]+'&e='+e0+'&t='+a0);let t1=document.getElementById('alarm1').value.split(':');let e1=document.getElementById('en1').checked?1:0;let a1=document.getElementById('atone1').value;fetch('/savealarm?i=1&h='+t1[0]+'&m='+t1[1]+'&e='+e1+'&t='+a1);setTimeout(()=>alert('✅ Alarms Saved!'),300);}"));
  server.sendContent(F("function updateBrightValue(v){document.getElementById('brightval').textContent=v;}"));
  server.sendContent(F("function saveBrightness(){let b=document.getElementById('bright').value;let a=document.getElementById('autoldr').checked?1:0;let lc=document.getElementById('ldrlowcut')?document.getElementById('ldrlowcut').value:150;let hc=document.getElementById('ldrhighcut')?document.getElementById('ldrhighcut').value:900;fetch('/savebright?b='+b+'&a='+a+'&lc='+lc+'&hc='+hc).then(r=>r.ok?alert('Saved!'):alert('Failed'));}"));
  server.sendContent(F("function toast(m){let e=document.getElementById('toast');if(!e)return;e.textContent=m;e.classList.add('show');clearTimeout(window._toast);window._toast=setTimeout(function(){e.classList.remove('show')},1800);}function hex(n){return n.toString(16).padStart(2,'0').toUpperCase();}function setPreview(r,g,b){let e=document.getElementById('colorpreview'),h=document.getElementById('hexpreview');if(!e)return;e.style.background='linear-gradient(135deg,rgb('+r+','+g+','+b+'),rgba('+r+','+g+','+b+',.35))';e.style.boxShadow='0 0 42px rgba('+r+','+g+','+b+',.30),inset 0 1px 0 rgba(255,255,255,.22)';if(h)h.textContent='#'+hex(r)+hex(g)+hex(b);}function syncPalette(v){let i=parseInt(v||0);document.querySelectorAll('.swatch').forEach(function(e){e.classList.toggle('selected',parseInt(e.dataset.i)==i)});let p=[[255,0,0],[255,255,0],[255,165,0],[255,192,64],[0,255,255],[0,255,0],[0,200,140],[128,0,255],[255,0,255],[0,128,255],[0,40,255],[255,255,255]][i]||[255,0,0];setPreview(p[0],p[1],p[2]);}function pickPalette(i){let s=document.getElementById('staticcolor');if(s){s.value=i;syncPalette(i)}let m=document.getElementById('colormode');if(m&&m.value!='0'&&m.value!='1')m.value='0';toggleColorOpts();}function pickMode(m){document.getElementById('colormode').value=m;toggleColorOpts();}function toggleColorOpts(){let m=document.getElementById('colormode').value;document.getElementById('staticopts').style.display=(m=='0'||m=='1')?'block':'none';document.getElementById('rainbowopts').style.display=(m=='2')?'block':'none';document.getElementById('rgbopts').style.display=(m=='3')?'block':'none';document.getElementById('sweepopts').style.display=(m=='4')?'block':'none';document.querySelectorAll('.mode-chip').forEach(function(e,i){e.classList.toggle('active',i==m)});if(m=='3')updateRGBPreview();else if(m=='0'||m=='1'){let s=document.getElementById('staticcolor');if(s)syncPalette(s.value)}else if(m=='2')setPreview(255,80,180);else setPreview(80,160,255);}function updateRGBPreview(){let r=document.getElementById('customr').value,g=document.getElementById('customg').value,b=document.getElementById('customb').value;document.getElementById('rval').textContent=r;document.getElementById('gval').textContent=g;document.getElementById('bval').textContent=b;let e=document.getElementById('rgbpreview');if(e)e.style.background='rgb('+r+','+g+','+b+')';setPreview(+r,+g,+b);}function saveColor(){let m=document.getElementById('colormode').value,sc=document.getElementById('staticcolor')?document.getElementById('staticcolor').value:0,ci=document.getElementById('colorint')?document.getElementById('colorint').value:5,r=document.getElementById('customr')?document.getElementById('customr').value:0,g=document.getElementById('customg')?document.getElementById('customg').value:0,b=document.getElementById('customb')?document.getElementById('customb').value:0,spdEl=(m=='2')?document.getElementById('animspeed_rb'):(m=='4')?document.getElementById('animspeed_sw'):null,spd=spdEl?spdEl.value:5;fetch('/savecolor?m='+m+'&sc='+sc+'&ci='+ci+'&r='+r+'&g='+g+'&b='+b+'&spd='+spd).then(function(r){if(!r.ok)throw 0;toast('Color Studio saved');}).catch(function(){toast('Save failed')});}"));
  server.sendContent(F("function togglePassword(){document.getElementById('passdiv').style.display=document.getElementById('wifihaspass').checked?'block':'none';}"));
  server.sendContent(F("function toggleStaticIP(){document.getElementById('staticdiv').style.display=document.getElementById('staticen').checked?'block':'none';}"));
  server.sendContent(F("function saveWiFi(){let en=document.getElementById('wifienable').checked?1:0;let ssid=encodeURIComponent(document.getElementById('wifissid').value);let haspass=document.getElementById('wifihaspass').checked?1:0;let pass=haspass?encodeURIComponent(document.getElementById('wifipass').value):'';let staticen=document.getElementById('staticen').checked?1:0;let sip=document.getElementById('sip').value;let gip=document.getElementById('gip').value;let snip=document.getElementById('snip').value;if(en&&ssid.length<2){alert('WiFi name দিন!');return;}if(confirm('Restart হবে?')){fetch('/savewifi?en='+en+'&ssid='+ssid+'&haspass='+haspass+'&pass='+pass+'&staticen='+staticen+'&sip='+encodeURIComponent(sip)+'&gip='+encodeURIComponent(gip)+'&snip='+encodeURIComponent(snip)).then(r=>r.ok?alert('Saved!'):alert('Failed'));}}"));
  server.sendContent(F("function saveDateSettings(){let e=document.getElementById('showEnglishDate').checked?1:0;let b=document.getElementById('showBanglaDate').checked?1:0;fetch('/savedatesettings?eng='+e+'&bangla='+b).then(r=>r.ok?alert('Saved!'):alert('Failed'));}"));
  server.sendContent(F("function toneModeChange(){let m=document.querySelector('input[name=tmode]:checked').value;document.getElementById('fixedtonediv').style.display=(m=='0')?'block':'none';}"));
  server.sendContent(F("function testTone(idx,btn){btn.textContent='▶ Playing...';btn.disabled=true;fetch('/testtone?idx='+idx).then(()=>{setTimeout(()=>{btn.disabled=false;btn.textContent='▶ Test';},3000);}).catch(()=>{btn.disabled=false;btn.textContent='▶ Test';});}"  ));
  server.sendContent(F("function saveTone(){let mode=document.querySelector('input[name=tmode]:checked').value;let idx=document.getElementById('toneidx')?document.getElementById('toneidx').value:0;let hb=document.getElementById('hourlybeep2').checked?1:0;let sd=document.getElementById('showdate')?(document.getElementById('showdate').checked?1:0):1;fetch('/savetone?mode='+mode+'&idx='+idx).then(r=>r.ok?alert('✅ Tone Saved!'):alert('Failed'));fetch('/savesettings?f='+(document.getElementById('fmt12').checked?1:0)+'&sd='+sd+'&cb='+(document.getElementById('colonblink').checked?1:0)+'&hb='+hb);}"));
  server.sendContent(F("function escHtml(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\"/g,'&quot;').replace(/'/g,'&#39;');}"));
  server.sendContent(F("function scanWiFi(){let btn=document.getElementById('scanbtn');let res=document.getElementById('scanresult');btn.textContent='⏳ Scanning...';btn.disabled=true;res.innerHTML='<p style=\"color:#888\">খুঁজছে...</p>';fetch('/scanwifi').then(r=>r.json()).then(nets=>{btn.textContent='🔍 SCAN';btn.disabled=false;if(!nets.length){res.innerHTML='<p style=\"color:#f44\">কোনো নেটওয়ার্ক নেই</p>';return;}let h='';nets.sort((a,b)=>b.sig-a.sig).forEach(n=>{let s=escHtml(n.ssid);let bars=n.sig>=75?'▂▄▆█':n.sig>=50?'▂▄▆_':n.sig>=25?'▂▄__':'▂___';let col=n.sig>=75?'#0f9':'#fa0';h+='<div onclick=\"selectNet(this)\" data-ssid=\"'+s+'\" style=\"cursor:pointer;display:flex;justify-content:space-between;align-items:center;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.08);border-radius:8px;padding:10px 14px;margin:4px 0\"><span style=\"font-weight:600\">'+s+'</span><span style=\"display:flex;gap:8px;align-items:center\"><small style=\"color:#888\">'+n.rssi+'dBm</small><span style=\"color:'+col+';font-family:monospace\">'+bars+'</span><span>'+(n.enc?'🔒':'🔓')+'</span></span></div>';});res.innerHTML=h;}).catch(()=>{btn.textContent='🔍 SCAN';btn.disabled=false;res.innerHTML='<p style=\"color:#f44\">Error</p>';});}"));
  server.sendContent(F("function selectNet(el){let ssid=el.getAttribute('data-ssid');document.getElementById('wifissid').value=ssid;document.getElementById('wifienable').checked=true;document.getElementById('wifihaspass').checked=true;document.getElementById('passdiv').style.display='block';document.getElementById('wifipass').focus();document.getElementById('scanresult').innerHTML='<div style=\"color:#0f9;padding:8px\">✅ \"'+escHtml(ssid)+'\" selected</div>';}"));
  server.sendContent(F("function togglePlaylist(){fetch('/toggleplaylist').then(r=>r.text()).then(st=>{document.getElementById('plstatus').textContent=st;document.getElementById('plstatus').className=st=='ON'?'status-on':'status-off';document.getElementById('plbtn').textContent=st=='ON'?'DISABLE PLAYLIST':'ENABLE PLAYLIST';document.getElementById('plbtn').className=st=='ON'?'btn-red btn btn-sm':'btn-green btn btn-sm';}).catch(()=>alert('Error'));}"));
  server.sendContent(F("function stepModeChange(i){let m=document.getElementById('m'+i).value;document.getElementById('sci'+i).style.display=(m=='0'||m=='1')?'block':'none';var ccd=document.getElementById('sccdiv'+i);if(ccd)ccd.style.display=(m=='0'||m=='1')?'block':'none';document.getElementById('srgb'+i).style.display=(m=='3')?'block':'none';document.getElementById('sspd'+i).style.display=(m=='2'||m=='4')?'block':'none';updateStepVisual(i);}function updateStepVisual(i){let m=document.getElementById('m'+i),sw=document.getElementById('stepswatch'+i),meta=document.getElementById('stepmeta'+i),dur=document.getElementById('d'+i),card=document.getElementById('stepcard'+i);if(!m||!sw)return;let mi=parseInt(m.value),names=['Static','Smooth Fade','Rainbow','Custom RGB','Sweep Random'];if(meta){var ccEl=document.getElementById('cc'+i),ccTxt='';if(ccEl&&(mi==0||mi==1)){if(dur&&ccEl.value)ccEl.max=dur.value;if(parseInt(ccEl.value)<parseInt(dur?dur.value:30))ccTxt=' • change every '+ccEl.value+'s';}meta.textContent=names[mi]+' • '+(dur?dur.value:30)+' sec'+ccTxt;}if(dur&&document.getElementById('stepdur'+i))document.getElementById('stepdur'+i).textContent=dur.value+'s';let p=[[255,0,0],[255,255,0],[255,165,0],[255,192,64],[0,255,255],[0,255,0],[0,200,140],[128,0,255],[255,0,255],[0,128,255],[0,40,255],[255,255,255]][parseInt((document.getElementById('ci'+i)||{}).value||0)]||[255,0,0];if(mi==3)p=[+(document.getElementById('r'+i)||{}).value||255,+(document.getElementById('g'+i)||{}).value||0,+(document.getElementById('b'+i)||{}).value||0];if(mi==2)p=[255,80,180];if(mi==4)p=[80,160,255];sw.style.background='rgb('+p[0]+','+p[1]+','+p[2]+')';if(card)card.style.setProperty('--step-color','rgb('+p[0]+','+p[1]+','+p[2]+')');}function stepDurInput(el,i){var e=document.getElementById('cc'+i);if(e)e.max=el.value;updateStepVisual(i);}function buildSteps(){let cnt=Math.min(8,parseInt(document.getElementById('plcnt').value)||1),box=document.getElementById('plsteps');box.innerHTML='';const cn=['Red','Yellow','Orange','Gold','Cyan','Green','Teal','Purple','Magenta','Blue','Deep Blue','White'];for(let i=0;i<cnt;i++){box.innerHTML+='<div class=\"step-card\" id=\"stepcard'+i+'\"><div class=\"step-top\"><div class=\"step-no\">'+(i+1)+'</div><div class=\"step-swatch\" id=\"stepswatch'+i+'\"></div><div class=\"step-title\"><b>SCENE '+(i+1)+'</b><span id=\"stepmeta'+i+'\">Static • 30 sec</span></div><span class=\"step-mini\" id=\"stepdur'+i+'\">30s</span></div><label>Mode:<select id=\"m'+i+'\" onchange=\"stepModeChange('+i+')\"><option value=\"0\">Static</option><option value=\"1\">Smooth Fade</option><option value=\"2\">Rainbow</option><option value=\"3\">Custom RGB</option><option value=\"4\">Sweep Random</option></select></label><div id=\"sci'+i+'\"><label>Color:<select id=\"ci'+i+'\" onchange=\"updateStepVisual('+i+')\">'+cn.map((c,j)=>\"<option value='\"+j+\"'>\"+c+\"</option>\").join('')+'</select></label></div><div id=\"srgb'+i+'\" style=\"display:none\"><div class=\"range-wrap\"><span style=\"color:#ff6666;min-width:20px;font-weight:700\">R</span><input type=\"range\" id=\"r'+i+'\" min=\"0\" max=\"255\" value=\"255\" oninput=\"this.nextElementSibling.textContent=this.value;updateStepVisual('+i+')\"><span class=\"range-val\" id=\"rv'+i+'\">255</span></div><div class=\"range-wrap\"><span style=\"color:#66ff66;min-width:20px;font-weight:700\">G</span><input type=\"range\" id=\"g'+i+'\" min=\"0\" max=\"255\" value=\"0\" oninput=\"this.nextElementSibling.textContent=this.value;updateStepVisual('+i+')\"><span class=\"range-val\" id=\"gv'+i+'\">0</span></div><div class=\"range-wrap\"><span style=\"color:#6699ff;min-width:20px;font-weight:700\">B</span><input type=\"range\" id=\"b'+i+'\" min=\"0\" max=\"255\" value=\"0\" oninput=\"this.nextElementSibling.textContent=this.value;updateStepVisual('+i+')\"><span class=\"range-val\" id=\"bv'+i+'\">0</span></div></div><div style=\"display:flex;gap:10px;flex-wrap:wrap;align-items:flex-end\"><label>Duration (sec):<input type=\"number\" id=\"d'+i+'\" min=\"1\" max=\"255\" value=\"30\" style=\"width:90px\" oninput=\"stepDurInput(this,'+i+')\"></label><div id=\"sccdiv'+i+'\" style=\"display:block\"><label title=\"এই সেকেন্ড পরপর পরের প্যালেট কালারে বদলাবে\">Color change sec:<input type=\"number\" id=\"cc'+i+'\" min=\"1\" max=\"30\" value=\"30\" style=\"width:80px\" oninput=\"updateStepVisual('+i+')\"></label></div></div><div id=\"sspd'+i+'\" style=\"display:none;background:rgba(255,200,50,.06);border-radius:8px;padding:10px;margin:6px 0;border:1px solid rgba(240,192,64,.15)\"><div style=\"font-size:11px;font-weight:700;color:#f0c040;margin-bottom:6px\">⚡ ANIMATION SPEED</div><div class=\"range-wrap\"><span style=\"font-size:11px;color:#888;min-width:34px\">দ্রুত</span><input type=\"range\" id=\"spd'+i+'\" min=\"1\" max=\"10\" value=\"5\" oninput=\"this.nextElementSibling.nextElementSibling.textContent=this.value\"><span style=\"font-size:11px;color:#888;min-width:28px\">ধীর</span><span class=\"range-val\" id=\"sv'+i+'\">5</span></div></div></div>';}}function savePlaylist(){let cnt=Math.min(8,parseInt(document.getElementById('plcnt').value)||1),url='/saveplaylist?en=1&cnt='+cnt;for(let i=0;i<cnt;i++){let m=document.getElementById('m'+i)?.value||0,ci=document.getElementById('ci'+i)?.value||0,r=document.getElementById('r'+i)?.value||0,g=document.getElementById('g'+i)?.value||0,b=document.getElementById('b'+i)?.value||0,d=document.getElementById('d'+i)?.value||30,spd=document.getElementById('spd'+i)?.value||5,cc=document.getElementById('cc'+i)?.value||d;url+='&m'+i+'='+m+'&ci'+i+'='+ci+'&r'+i+'='+r+'&g'+i+'='+g+'&b'+i+'='+b+'&d'+i+'='+d+'&spd'+i+'='+spd+'&cc'+i+'='+cc;}fetch(url).then(r=>{if(!r.ok)throw 0;let s=document.getElementById('playliststate');if(s)s.textContent='✓ Playlist saved to clock memory';toast('✓ Smart Playlist saved');}).catch(()=>toast('Playlist save failed'));}function previewPlaylist(){let n=Math.min(8,parseInt(document.getElementById('plcnt').value)||1),i=0;toast('▶ Previewing '+n+' scenes');let timer=setInterval(()=>{let c=document.getElementById('stepcard'+i);if(c){document.querySelectorAll('.step-card').forEach(x=>x.style.boxShadow='');c.style.boxShadow='0 0 0 1px var(--gold),0 0 28px rgba(240,192,64,.16)';}i++;if(i>=n){clearInterval(timer);setTimeout(()=>document.querySelectorAll('.step-card').forEach(x=>x.style.boxShadow=''),500);}},700);}function playlistPreset(k){let n=Math.min(8,parseInt(document.getElementById('plcnt').value)||4),colors=k==0?[0,2,0,3,8,0,9,4]:k==1?[9,4,6,2,11,4,9,6]:k==2?[2,0,3,8,1,2,0,3]:[0,2,4,6,7,8,9,11];for(let i=0;i<n;i++){let c=document.getElementById('ci'+i),m=document.getElementById('m'+i),d=document.getElementById('d'+i);if(c)c.value=colors[i%colors.length];if(m)m.value=(k==3)?2:(i%3==1?1:0);let dv=(k==3)?18:30;if(d)d.value=dv;let cc=document.getElementById('cc'+i);if(cc){cc.max=dv;cc.value=dv;}stepModeChange(i);}toast('Preset applied — save when ready');}window.addEventListener('load',()=>{toggleColorOpts();for(let i=0;i<8;i++)if(document.getElementById('m'+i))updateStepVisual(i);let pc=document.getElementById('plcnt'),meter=document.getElementById('playlistmeter');if(pc&&meter)meter.style.width=(parseInt(pc.value||1)/8*100)+'%';});"));
server.sendContent(F("function toggleToneRange(){document.getElementById('tonerangediv').style.display=document.getElementById('tonerangeen').checked?'block':'none';}"));
  server.sendContent(F("function saveToneRange(){let en=document.getElementById('tonerangeen').checked?1:0;let sh=document.getElementById('tonestarthr').value.split(':')[0];let eh=document.getElementById('toneendhr').value.split(':')[0];fetch('/savetonerange?en='+en+'&sh='+sh+'&eh='+eh).then(r=>r.ok?alert('✅ Time Range Saved!'):alert('Failed')).catch(()=>alert('Error'));}"));
  server.sendContent(F("function testToneBtn(alarmIdx,btn){let sel=document.getElementById('atone'+alarmIdx);let idx=sel?sel.value:0;btn.textContent='▶ Playing...';btn.disabled=true;fetch('/testtone?idx='+idx).then(()=>{setTimeout(()=>{btn.disabled=false;btn.textContent='▶ Test This Alarm Tone';},4000);}).catch(()=>{btn.disabled=false;btn.textContent='▶ Test This Alarm Tone';});}"));
  // FIX (v5 audit): আগে প্রতি ৬০ সেকেন্ডে unconditionally পুরো পেজ reload হতো —
  // Color Playlist step editor বা WiFi/RGB ফর্মে কেউ টাইপ/স্লাইড করার সময়
  // reload হলে সব unsaved change মুছে যেত। এখন কোনো input/select/textarea তে
  // focus থাকলে বা গত reload attempt-এর পরে কিছু edit করা হলে reload স্কিপ হয়।
  server.sendContent(F(
    "let _dirty=false;"
    "document.addEventListener('input',e=>{if(['INPUT','SELECT','TEXTAREA'].includes(e.target.tagName))_dirty=true;});"
    "setInterval(()=>{let a=document.activeElement,editing=a&&['INPUT','SELECT','TEXTAREA'].includes(a.tagName);"
    "if(!editing&&!_dirty)location.reload();},60000);"
  ));
  // ── PREMIUM 3D TILT INTERACTION ──
  server.sendContent(F("(function(){var c=document.getElementById('timebox3d');if(!c)return;var raf=null;"
    "function tilt(x,y){var r=c.getBoundingClientRect();var px=(x-r.left)/r.width-0.5;var py=(y-r.top)/r.height-0.5;"
    "if(raf)cancelAnimationFrame(raf);raf=requestAnimationFrame(function(){c.style.transform='rotateY('+(px*14)+'deg) rotateX('+(-py*14)+'deg) translateZ(6px)';});}"
    "function reset(){if(raf)cancelAnimationFrame(raf);c.style.transform='rotateY(0deg) rotateX(0deg) translateZ(0px)';}"
    "c.addEventListener('mousemove',function(e){tilt(e.clientX,e.clientY);});"
    "c.addEventListener('mouseleave',reset);"
    "c.addEventListener('touchmove',function(e){if(e.touches&&e.touches[0]){tilt(e.touches[0].clientX,e.touches[0].clientY);}},{passive:true});"
    "c.addEventListener('touchend',reset);"
    "if(window.DeviceOrientationEvent){window.addEventListener('deviceorientation',function(e){"
    "if(e.gamma===null||e.beta===null)return;var ry=Math.max(-12,Math.min(12,e.gamma/3));var rx=Math.max(-12,Math.min(12,(e.beta-45)/6));"
    "c.style.transform='rotateY('+ry+'deg) rotateX('+(-rx)+'deg) translateZ(6px)';});}"
    "})();"));
  server.sendContent(F("</script></body></html>"));
  server.sendContent("");
}

// ========== SETTINGS PAGE ==========
void handleSettings() {
  if (webuiPasswordEnabled) {
    if (!server.authenticate("admin", webui_pass)) {
      return server.requestAuthentication(BASIC_AUTH, "Sultan Clock", "Password প্রয়োজন");
    }
  }

  String html = F("<!DOCTYPE html><html><head>");
  html += F("<meta name='viewport' content='width=device-width,initial-scale=1'>");
  html += F("<meta charset='UTF-8'><title>Settings — Sultan Clock</title>");
  html += F("<style>");
  html += F("*{box-sizing:border-box;margin:0;padding:0}");
  html += F("body{font-family:'Segoe UI',Arial,sans-serif;background:#0a0a0f;color:#e8e8f0;min-height:100vh;padding:16px;background-image:radial-gradient(ellipse at 20% 20%,rgba(240,192,64,0.06) 0%,transparent 50%)}");
  html += F(".box{max-width:520px;margin:0 auto}");
  html += F(".header{text-align:center;padding:24px 0 20px;border-bottom:1px solid rgba(240,192,64,0.2);margin-bottom:20px}");
  html += F(".title{font-size:20px;font-weight:700;color:#f0c040;letter-spacing:3px;text-transform:uppercase}");
  html += F(".sub{font-size:12px;color:#888;margin-top:4px;letter-spacing:2px}");
  html += F(".card{background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.09);border-radius:16px;padding:20px;margin:12px 0;position:relative;overflow:hidden}");
  html += F(".card::before{content:'';position:absolute;top:0;left:0;right:0;height:2px;background:linear-gradient(90deg,transparent,#f0c040,transparent);opacity:0.5}");
  html += F(".card-title{font-size:13px;font-weight:700;color:#f0c040;letter-spacing:2px;text-transform:uppercase;margin-bottom:14px;display:flex;align-items:center;gap:8px}");
  html += F("label{display:block;margin:10px 0;font-size:14px;color:#ccc;font-weight:600}");
  html += F("input[type=text],input[type=password]{width:100%;padding:11px 14px;border:1px solid rgba(255,255,255,0.1);border-radius:10px;background:rgba(255,255,255,0.05);color:#e8e8f0;font-size:14px;outline:none;transition:border 0.2s}");
  html += F("input:focus{border-color:#f0c040;box-shadow:0 0 0 2px rgba(240,192,64,0.15)}");
  html += F("input[type=checkbox]{width:16px;height:16px;margin-right:8px;accent-color:#f0c040;cursor:pointer;vertical-align:middle}");
  html += F(".btn{display:block;width:100%;padding:13px;border:none;border-radius:12px;font-size:12px;font-weight:700;cursor:pointer;margin-top:10px;letter-spacing:2px;text-transform:uppercase;text-align:center;text-decoration:none;transition:all 0.25s}");
  html += F(".btn-gold{background:linear-gradient(135deg,#b8860b,#f0c040);color:#000;box-shadow:0 4px 20px rgba(240,192,64,0.25)}");
  html += F(".btn-gold:hover{transform:translateY(-2px)}");
  html += F(".btn-blue{background:linear-gradient(135deg,#0099bb,#00e5ff);color:#000}");
  html += F(".btn-red{background:linear-gradient(135deg,#c0392b,#ff4466);color:#fff}");
  html += F(".btn-green{background:linear-gradient(135deg,#00b36b,#00ff99);color:#000}");
  html += F(".btn-gray{background:rgba(255,255,255,0.1);color:#ccc;border:1px solid rgba(255,255,255,0.15)}");
  html += F(".hint{font-size:12px;color:#666;margin-top:5px;line-height:1.5}");
  html += F(".divider{border:none;border-top:1px solid rgba(255,255,255,0.07);margin:14px 0}");
  html += F(".msg-ok{background:rgba(0,255,153,0.1);border:1px solid rgba(0,255,153,0.3);border-radius:10px;padding:10px 14px;font-size:13px;color:#00ff99;margin:10px 0;display:none}");
  html += F(".msg-err{background:rgba(255,68,68,0.1);border:1px solid rgba(255,68,68,0.3);border-radius:10px;padding:10px 14px;font-size:13px;color:#ff8888;margin:10px 0;display:none}");
  html += F(".nav-back{display:inline-flex;align-items:center;gap:6px;color:#f0c040;text-decoration:none;font-size:13px;font-weight:600;margin-bottom:16px;opacity:0.8}");
  html += F(".nav-back:hover{opacity:1}");
  html += F("</style></head><body><div class='box'>");
  server.setContentLength(CONTENT_LENGTH_UNKNOWN);
  server.send(200, F("text/html"), html);
  html = "";

  html += F("<a href='/' class='nav-back'>← Home এ ফিরে যাও</a>");

  html += F("<div class='header'>");
  html += F("<div class='title'>⚙️ Settings</div>");
  html += F("<div class='sub'>Sultan Clock Configuration</div>");
  html += F("</div>");

  // ── PASSWORD PROTECTION CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'>🔒 Web UI Password</div>");
  html += F("<p class='hint' style='margin-bottom:12px'>Password চালু থাকলে Web UI তে ঢুকতে username: <b>admin</b> এবং নিচের password লাগবে।</p>");
  html += F("<label><input type='checkbox' id='pwen' ");
  if (webuiPasswordEnabled) html += F("checked");
  html += F("> Password Protection চালু রাখো</label>");
  html += F("<hr class='divider'>");
  html += F("<div style='font-size:13px;font-weight:700;color:#f0c040;margin-bottom:10px'>🔑 Password পরিবর্তন করো</div>");
  html += F("<label>বর্তমান Password:<input type='password' id='oldpass' placeholder='Current password'></label>");
  html += F("<label>নতুন Password:<input type='password' id='newpass' placeholder='নতুন password (কমপক্ষে ৪ অক্ষর)'></label>");
  html += F("<label>নতুন Password আবার লিখো:<input type='password' id='newpass2' placeholder='Confirm new password'></label>");
  html += F("<div id='pmsg_ok' class='msg-ok'>✅ Password পরিবর্তন হয়েছে!</div>");
  html += F("<div id='pmsg_err' class='msg-err'></div>");
  html += F("<button onclick='changePass()' class='btn btn-gold'>🔑 PASSWORD SAVE করো</button>");
  html += F("</div>");
  server.sendContent(html); html = "";

  // ── OTA UPDATE CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'>🔧 Firmware Update (OTA)</div>");
  html += F("<p class='hint' style='margin-bottom:12px'>USB ছাড়াই নতুন code wireless এ upload করো।</p>");
  if (WiFi.status() == WL_CONNECTED) {
    html += F("<div style='background:rgba(0,255,153,0.08);border:1px solid rgba(0,255,153,0.2);border-radius:10px;padding:10px 14px;font-size:13px;color:#00ff99;margin-bottom:10px'>✅ WiFi connected — OTA ready<br>Clock IP: <b>") + WiFi.localIP().toString() + F("</b></div>");
    html += F("<a href='http://") + WiFi.localIP().toString() + F("/update' class='btn btn-blue'>🚀 OTA Update Page খোলো</a>");
  } else {
    html += F("<div style='background:rgba(255,68,68,0.08);border:1px solid rgba(255,68,68,0.2);border-radius:10px;padding:10px 14px;font-size:13px;color:#ff8888;margin-bottom:10px'>⚠️ WiFi connected নেই। Home WiFi connect করলে OTA কাজ করবে।</div>");
  }
  html += F("<a href='/ota' class='btn btn-gray' style='margin-top:8px'>📖 OTA নির্দেশিকা দেখো</a>");
  html += F("</div>");
  server.sendContent(html); html = "";

  // ── ABOUT CARD ──
  html += F("<div class='card'>");
  html += F("<div class='card-title'>ℹ️ About This Clock</div>");
  html += F("<div style='line-height:2;font-size:14px'>");
  html += F("<div style='display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)'><span style='color:#888'>Developer</span><span style='color:#f0c040;font-weight:700'>MD: SULTAN MAHAMUD</span></div>");
  html += F("<div style='display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)'><span style='color:#888'>Mobile</span><span>01740-236384</span></div>");
  html += F("<div style='display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)'><span style='color:#888'>Email</span><span style='font-size:12px'>sultanmahamud5497@gmail.com</span></div>");
  html += F("<div style='display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)'><span style='color:#888'>Version</span><span style='color:#00e5ff'>v3.2</span></div>");
  html += F("<div style='display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)'><span style='color:#888'>Hardware</span><span style='font-size:12px'>ESP8266 + DS3231 + WS2812B</span></div>");
  html += F("<div style='display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)'><span style='color:#888'>LEDs</span><span>30× WS2812B (D6)</span></div>");
  html += F("<div style='display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)'><span style='color:#888'>Buzzer</span><span>Passive (D5)</span></div>");
  html += F("<div style='display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.06)'><span style='color:#888'>Ringtones</span><span>13 RTTTL tones</span></div>");
  html += F("<div style='display:flex;justify-content:space-between;padding:6px 0'><span style='color:#888'>Updated</span><span>2026-04-14</span></div>");
  html += F("</div>");
  html += F("<div style='text-align:center;margin-top:14px;font-size:20px;letter-spacing:4px;color:#f0c040'>✦ ALHAMDULILLAH ✦</div>");
  html += F("</div>");
  server.sendContent(html); html = "";

  // JS
  html += F("<script>");
  html += F("function changePass(){");
  html += F("let old=document.getElementById('oldpass').value;");
  html += F("let np=document.getElementById('newpass').value;");
  html += F("let np2=document.getElementById('newpass2').value;");
  html += F("let ok=document.getElementById('pmsg_ok');");
  html += F("let err=document.getElementById('pmsg_err');");
  html += F("ok.style.display='none';err.style.display='none';");
  html += F("if(np.length<4){err.textContent='❌ Password কমপক্ষে ৪ অক্ষর হতে হবে।';err.style.display='block';return;}");
  html += F("if(np!==np2){err.textContent='❌ নতুন password দুটো মিলছে না।';err.style.display='block';return;}");
  html += F("let en=document.getElementById('pwen').checked?1:0;");
  html += F("fetch('/changepassword?old='+encodeURIComponent(old)+'&new='+encodeURIComponent(np)+'&en='+en)");
  html += F(".then(r=>r.text()).then(t=>{if(t==='OK'){ok.style.display='block';document.getElementById('oldpass').value='';document.getElementById('newpass').value='';document.getElementById('newpass2').value='';}else{err.textContent='❌ '+t;err.style.display='block';}})");
  html += F(".catch(()=>{err.textContent='❌ Error';err.style.display='block';});");
  html += F("}");
  html += F("</script>");
  html += F("</div></body></html>");
  server.sendContent(html);
  server.sendContent("");
}

// ========== CHANGE PASSWORD HANDLER ==========
void handleChangePassword() {
  if (webuiPasswordEnabled) {
    if (!server.authenticate("admin", webui_pass)) {
      return server.requestAuthentication(BASIC_AUTH, "Sultan Clock", "Password প্রয়োজন");
    }
  }
  String oldPass = server.arg(F("old"));
  String newPass = server.arg(F("new"));
  bool   enPass  = server.arg(F("en")) == F("1");

  // Verify old password (if protection currently enabled)
  if (webuiPasswordEnabled && oldPass != String(webui_pass)) {
    server.send(200, F("text/plain"), F("বর্তমান password ভুল!"));
    return;
  }
  if (newPass.length() < 4) {
    server.send(200, F("text/plain"), F("Password কমপক্ষে ৪ অক্ষর হতে হবে।"));
    return;
  }

  webuiPasswordEnabled = enPass;
  newPass.toCharArray(webui_pass, sizeof(webui_pass));
  saveSettings();
  Serial.println(F("Web UI password changed."));
  server.send(200, F("text/plain"), F("OK"));
}

// ========== CHECK AUTH HANDLER ==========
// FIX: Previously declared but never implemented — added
void handleCheckAuth() {
  if (webuiPasswordEnabled) {
    if (!server.authenticate("admin", webui_pass)) {
      return server.requestAuthentication(BASIC_AUTH, "Sultan Clock", "Password প্রয়োজন");
    }
  }
  server.send(200, F("text/plain"), F("OK"));
}

// ========== OTA INFO PAGE ==========
void handleOTAPage() {
  if (!requireAuth()) return;
  String html = F("<!DOCTYPE html><html><head>");
  html += F("<meta name='viewport' content='width=device-width,initial-scale=1'>");
  html += F("<meta charset='UTF-8'><title>Firmware Update</title>");
  html += F("<style>");
  html += F("body{font-family:Arial,sans-serif;background:linear-gradient(135deg,#0a0a0f,#1a1a2e);color:#e8e8f0;padding:20px;margin:0;min-height:100vh}");
  html += F(".box{max-width:560px;margin:0 auto}");
  html += F("h1{text-align:center;color:#f0c040;font-size:22px;margin-bottom:6px}");
  html += F(".sub{text-align:center;color:#888;font-size:13px;margin-bottom:24px}");
  html += F(".card{background:rgba(255,255,255,0.06);border:1px solid rgba(255,255,255,0.1);border-radius:16px;padding:20px;margin:14px 0}");
  html += F("h3{color:#f0c040;margin-bottom:12px;font-size:15px}");
  html += F(".step{display:flex;gap:12px;align-items:flex-start;margin:10px 0;padding:10px;background:rgba(255,255,255,0.04);border-radius:10px}");
  html += F(".num{background:#f0c040;color:#000;font-weight:700;font-size:14px;min-width:26px;height:26px;border-radius:50%;display:flex;align-items:center;justify-content:center}");
  html += F(".txt{font-size:13px;line-height:1.6;color:#ccc}");
  html += F(".txt b{color:#fff}");
  html += F(".warn{background:rgba(255,68,68,0.12);border:1px solid rgba(255,68,68,0.3);border-radius:10px;padding:12px 16px;font-size:13px;color:#ff8888;margin:12px 0}");
  html += F(".ok{background:rgba(0,255,153,0.08);border:1px solid rgba(0,255,153,0.25);border-radius:10px;padding:12px 16px;font-size:13px;color:#00ff99;margin:12px 0}");
  html += F(".btn{display:block;width:100%;padding:14px;border:none;border-radius:12px;background:linear-gradient(135deg,#b8860b,#f0c040);color:#000;font-size:14px;font-weight:700;cursor:pointer;text-align:center;text-decoration:none;margin-top:12px;letter-spacing:1px}");
  html += F(".btn-blue{background:linear-gradient(135deg,#0099bb,#00e5ff);color:#000}");
  html += F("code{background:rgba(255,255,255,0.1);padding:2px 8px;border-radius:5px;font-family:monospace;font-size:13px;color:#00e5ff}");
  html += F("</style></head><body><div class='box'>");

  // FIX (v5 - memory): chunked streaming, handleRoot()-এর মতোই।
  server.setContentLength(CONTENT_LENGTH_UNKNOWN);
  server.send(200, F("text/html"), html);
  html = "";

  html += F("<h1>🔧 Firmware Update (OTA)</h1>");
  html += F("<p class='sub'>USB ছাড়াই নতুন code clock এ upload করো</p>");

  // WiFi status check
  if (WiFi.status() != WL_CONNECTED) {
    html += F("<div class='warn'>⚠️ WiFi connected নেই! OTA update করতে clock কে home WiFi তে connect করতে হবে।<br><br>নিচে যাও → Web UI → WiFi Settings → SSID ও Password দিয়ে Save করো।</div>");
  } else {
    html += F("<div class='ok'>✅ WiFi connected: <b>") + htmlEscape(String(wifi_ssid)) + F("</b><br>Clock IP: <b>") + WiFi.localIP().toString() + F("</b><br>OTA Update page: <b>http://") + WiFi.localIP().toString() + F("/update</b></div>");
  }
  server.sendContent(html); html = "";

  // Step by step guide
  html += F("<div class='card'><h3>📋 ধাপে ধাপে নির্দেশিকা</h3>");

  html += F("<div class='step'><div class='num'>1</div><div class='txt'><b>Arduino IDE খোলো</b> এবং সর্বশেষ <b>sultan_clock_v?.ino</b> file টা open করো।</div></div>");

  html += F("<div class='step'><div class='num'>2</div><div class='txt'><b>Board ও Port select করো:</b><br>");
  html += F("Tools → Board → <b>ESP8266 Boards → NodeMCU 1.0 (ESP-12E)</b><br>");
  html += F("Tools → Upload Speed → <b>115200</b></div></div>");

  html += F("<div class='step'><div class='num'>3</div><div class='txt'><b>.bin file তৈরি করো (USB লাগবে না!):</b><br>");
  html += F("Arduino IDE menu → <b>Sketch → Export Compiled Binary</b><br>");
  html += F("কিছুক্ষণ অপেক্ষা করো — একটা <code>.bin</code> file তৈরি হবে।<br>");
  html += F("File টা সাধারণত <code>sultan_clock_v?.ino.bin</code> নামে <b>sketch folder</b> এ save হয়।</div></div>");
  server.sendContent(html); html = "";

  html += F("<div class='step'><div class='num'>4</div><div class='txt'><b>OTA Update page এ যাও:</b><br>");
  if (WiFi.status() == WL_CONNECTED) {
    html += F("ব্রাউজারে এই address লিখো: <code>http://") + WiFi.localIP().toString() + F("/update</code><br>");
  } else {
    html += F("Clock টা home WiFi তে connect করার পরে: <code>http://[Clock IP]/update</code><br>");
  }
  // FIX: Security — OTA credentials page source এ hardcoded দেখানো হচ্ছে না
  html += F("Login credentials টা device এর নিচে label এ দেখো।</div></div>");

  html += F("<div class='step'><div class='num'>5</div><div class='txt'><b>File upload করো:</b><br>");
  html += F("<b>Choose File</b> button এ click করে তৈরি করা <code>.bin</code> file টা select করো।<br>");
  html += F("তারপর <b>Update</b> button এ click করো — progress bar দেখাবে।</div></div>");

  html += F("<div class='step'><div class='num'>6</div><div class='txt'><b>Automatic restart:</b><br>");
  html += F("Upload শেষ হলে clock নিজে নিজে restart হবে এবং নতুন firmware চালু হবে। ✅</div></div>");
  html += F("</div>");

  // Warning
  html += F("<div class='warn'>⚠️ <b>গুরুত্বপূর্ণ:</b> Upload এর সময় clock এর power যেন না যায়। Upload হতে সাধারণত ৩০-৬০ সেকেন্ড লাগে।</div>");

  // Direct link button
  if (WiFi.status() == WL_CONNECTED) {
    html += F("<a href='http://") + WiFi.localIP().toString() + F("/update' class='btn btn-blue'>🚀 OTA Update Page খোলো → http://") + WiFi.localIP().toString() + F("/update</a>");
  }

  html += F("<a href='/' class='btn'>← Back to Home</a>");
  html += F("</div></body></html>");
  server.sendContent(html);
  server.sendContent("");
}

// ========== ABOUT PAGE ==========
void handleAbout() {
  String html = F("<!DOCTYPE html><html><head>");
  html += F("<meta name='viewport' content='width=device-width,initial-scale=1'>");
  html += F("<meta charset='UTF-8'><title>About</title>");
  html += F("<style>body{font-family:Arial,sans-serif;background:linear-gradient(135deg,#1e3c72,#2a5298);color:#fff;padding:20px;margin:0}");
  html += F(".container{max-width:650px;margin:0 auto;background:rgba(255,255,255,0.1);border-radius:20px;padding:25px;box-shadow:0 0 15px rgba(0,0,0,0.3)}");
  html += F("h1{text-align:center;color:#00eaff;text-shadow:0 0 10px #00eaff;margin-bottom:25px}");
  html += F(".card{background:rgba(255,255,255,0.12);border-radius:15px;padding:20px;margin:15px 0}");
  html += F("h3{border-bottom:2px solid rgba(255,255,255,0.3);padding-bottom:8px;color:#ffeb3b}");
  html += F("p{line-height:1.6}p strong{color:#00ffd5}");
  html += F(".btn{width:100%;padding:12px;border:none;border-radius:10px;background:#00d4ff;color:#fff;font-size:16px;cursor:pointer;margin-top:15px;text-decoration:none;display:block;text-align:center;transition:0.3s}");
  html += F(".btn:hover{background:#00a6d6}</style></head><body>");

  // FIX (v5 - memory): chunked streaming, handleRoot()-এর মতোই।
  server.setContentLength(CONTENT_LENGTH_UNKNOWN);
  server.send(200, F("text/html"), html);
  html = "";

  html += F("<div class='container'><h1>About This Clock</h1>");
  html += F("<div class='card'><h3>Developer Information</h3>");
  html += F("<p><strong>Name:</strong> MD: SULTAN MAHAMUD</p>");
  html += F("<p><strong>Mobile:</strong> 01740-236384</p>");
  html += F("<p><strong>Email:</strong> sultanmahamud5497@gmail.com</p>");
  html += F("<p><strong>Updated:</strong> 2026-09-17 (v6)</p></div>");
  html += F("<div class='card'><h3>Hardware</h3>");
  html += F("<p>&bull; ESP8266 &bull; DS3231 RTC (D1/D2) &bull; WS2812B 30 LEDs (D6) &bull; Passive Buzzer (D5) &bull; LDR (A0) &bull; D7 Light</p></div>");
  server.sendContent(html); html = "";

  html += F("<div class='card'><h3>v3.0 Changes</h3>");
  html += F("<p>&bull; 🎵 RTTTL Ringtone player added — hourly melody<br>&bull; 🎵 13 built-in tones (Smooth Criminal, Jingle Bells, Doom, Contra, Mario...)<br>&bull; 🎵 Fixed/Random/Sequential tone mode<br>&bull; 🌙 Brightness minimum 1 (রাতে প্রায় নিভু নিভু)<br>&bull; ❌ Islamic date removed (memory free)<br>&bull; 📶 WiFi Scan added<br>&bull; NTP UTC+6 fixed, Bangla year fixed</p></div>");
  html += F("<div class='card'><h3>Color Modes</h3>");
  html += F("<p>&bull; Static / Smooth Fade / Rainbow / Custom RGB / Sweep Random<br>");
  html += F("&bull; <strong>🎬 Color Playlist</strong> — ৮টা step, প্রতিটায় আলাদা mode, color, duration ও animation speed</p></div>");
  html += F("<a href='/' class='btn'>&larr; Back to Home</a></div></body></html>");
  server.sendContent(html);
  server.sendContent("");
}
