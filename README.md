# 台南上廁所

「台南上廁所」是以臺南市開放資料製作的 Android 公廁查詢 App。開啟後可查看距離目前位置最近的 **50 個場所**，在 OpenStreetMap 地圖探索廁所，或用 Google Maps 瀏覽選定的地點。

## 功能

- GPS 定位、直線距離排序，以及顯示公廁名稱、行政區域、距離與廁所類型標籤。
- 依地圖視野顯示所有已收錄場所；相鄰標記以數字聚合，同座標的多個場所可由清單選取。地圖右下角可一鍵回到目前位置。
- 點擊「Google Maps 瀏覽」查看地點；若沒有安裝 Google Maps，改用瀏覽器開啟。
- 設定頁可切換系統／淺色／深色模式、六種預設主題色，以及 Hue 滑桿自訂色彩；設定會保存在裝置上。
- 內建預先整理的離線公廁資料；地圖圖磚與外部地圖瀏覽需要網路。

## 快速開始

需求：**JDK 17、Android SDK 35、Android Studio 或 Gradle Wrapper**；建議使用具備 Google Play 服務的裝置。最低 Android 版本為 API 24。

1. 以 Android Studio 開啟專案，等待 Gradle 同步，連接裝置並執行 `app`；或在 Windows PowerShell 執行：

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

   macOS／Linux 請執行 `./gradlew :app:assembleDebug`。
2. 安裝 `app/build/outputs/apk/debug/app-debug.apk`，開啟 App 並允許定位。
3. 從清單選擇廁所，或切換至地圖瀏覽；使用方式見[使用指南](docs/使用指南.md)。

## 更新資料

原始 CSV `907f0805-d09d-41ea-80cd-563368c420f4.csv` 來自[臺南市公廁地址（資料集 7005）](https://data.gov.tw/dataset/7005)，目前有 **4,449 筆**。App 使用 `app/src/main/assets/toilets.json`，目前整理為 **2,383 個場所**。取得相同欄位格式的新 CSV 後，使用 Python 3.10 以上執行：

```powershell
python -B tools/prepare_toilets.py "新版資料.csv" --check
python -B tools/prepare_toilets.py "新版資料.csv"
.\gradlew.bat :app:assembleDebug
```

`--check` 僅驗證與統計，不會覆寫資產。規則、驗證方式與資料授權見[資料來源與更新](docs/資料來源與更新.md)。

## 專案文件

- [文件索引](docs/README.md)
- [使用指南](docs/使用指南.md)
- [系統架構與技術文件](docs/系統架構與技術文件.md)
- [系統設計文件](docs/系統設計文件.md)
- [資料來源與更新](docs/資料來源與更新.md)
- [授權與第三方資源](docs/授權與第三方資源.md)
- [貢獻指南](docs/貢獻指南.md)

## 授權與資料來源

本專案原創**程式碼**以 [MIT License](LICENSE) 授權，Copyright © 2026 mark216tw。臺南市公廁資料由**臺南市政府環境保護局**提供，適用[政府資料開放授權條款－第 1 版](https://data.gov.tw/license)，不因本專案採 MIT 而改變其授權。地圖資料 © OpenStreetMap contributors。詳細標示請見 [NOTICE.md](NOTICE.md)。

資料不含即時開放狀態、街道地址或路線距離；畫面顯示的距離是直線距離。
