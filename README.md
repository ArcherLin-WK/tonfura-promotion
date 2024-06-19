# 題目

設計一套搶購機制，規格：

每天定點23點發送優惠券，需要用戶提前1-5分鐘前先預約，優惠券的數量為預約用戶數量的20%，

搶購優惠券時，每個用戶只能搶一次，只有1分鐘的搶購時間，怎麼盡量保證用戶搶到的概率盡量貼近20%。

需要考慮有300人搶和30000人搶的場景。

設計表結構和索引、編寫主要程式碼。

# Design

## workflow

1. 系統在啟動時，載入預約時間及發放優惠券的區間，設定資料來源可由資料庫或專案設定檔(此範例是使用專案設定檔 [application.yaml](./src/main/resources/application.yaml))。
2. 預約時間開始時，符合下列條件的用戶可以預約優惠券，將預約用戶列表存放在 cache 並透過 queue 來非同步寫入資料庫記錄：
   - 用戶已登入 (可透過 session 或 token 來驗證)
   - 用戶未預約過
   - 用戶預約時間在預約時間範圍內
3. 在預約時間結束後，系統計算預約用戶數量(total amount of cache)的20%，並準備發放優惠券。
4. 搶購時間開始時，符合下列條件的用戶可以搶購優惠券，並使用同步鎖以避免在計算發放數量時出現 race condition 問題：
   - 用戶已登入 (可透過 session 或 token 來驗證)
   - 用戶已預約
   - 用戶未搶過
   - 用戶搶購時間在搶購時間範圍內
   - 優惠券數量未達到發放數量
5. 成功搶購的用戶將優惠券發放給用戶，並透過 queue 非同步的方式將發放記錄存放在資料庫。
* note:
  * cache: 使用 cache 來存放預約用戶列表，以避免在預約時間結束後，需要重新計算預約用戶數量時，需要重新查詢資料庫。
  * queue: 因應大部份資料庫特性都是讀多寫少，所以使用 queue 來非同步的方式將預約用戶列表和發放記錄存放在資料庫，以避免在請求流量大的時間，資料庫寫入效能影響系統效能。

## Data Schema

### user

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | User ID |
| account | String | User account |
| pwd | String | User password stored in hash |
| lastLogin | Timestamp | Last login time in UTC |

### activity

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | Activity ID |
| issuingTime | Timestamp | Activity start time in UTC |
| issuingDuration | String | Activity duration in ISO8601 format, like: P7D, PT12H, PT5M |
| reservingTime | Timestamp | Reserving time in UTC |
| reservingDuration | String | Reserving duration in ISO8601 format, like: PT5M |
| amount | Integer | Amount of coupons to be issued |

### promotion record

| Field      | Type | Description |
|------------| --- | --- |
| id         | UUID | Issued record ID |
| user       | UUID | User ID |
| activity   | UUID | Activity ID |
| reservedTime | Timestamp | Reserved time in UTC |
| coupon     | String | a string with 10 random characters |
| issuedTime | Timestamp | Issued time in UTC |

## API
> **_NOTE:_** 由於該專案並未實作身份驗證，所以使用者資料是透過 request body 傳遞，實際上可透過 session 或 token 來驗證並得知使用者身份。

* POST /activities/:id/reserve
  * Request:
    ```json
    {
      "user": "user id"
    }
    ```
  * Response:
    * success
      ```json
      {
        "reservedTime": "reserve time in UTC"
      }
      ```
    * failed
        ```json
        {
            "message": "error message"
        }
        ```
    
* POST /activities/:id/grab
  * Request:
    ```json
    {
      "user": "user id"
    }
    ```
  * Response:
    * success
      ```json
      {
        "coupon": "coupon code"
      }
      ```
    * failed
        ```json
        {
            "message": "error message"
        }
        ```

# How to run

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.
