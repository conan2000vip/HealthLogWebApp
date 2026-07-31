# HealthLogWebApp

健康管理（ヘルスログ）Webアプリケーション

アプリケーションに不具合が発生した場合、第三者（トレーナー等）がソースコードを取得し、同じ開発環境を再現できるように、本 README および DB 構築用 SQL ファイルを整備しています。

---

# 1. 開発環境

| 項目 | バージョン |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.16 |
| MySQL | 8.0 |
| Maven | 3.9 |
| MailHog（メールテスト用） | 最新版 |

### 主な使用技術

- Spring Boot
- Spring Data JPA
- Thymeleaf
- Spring Validation
- Spring Security
- Lombok

> ※ Lombok を使用しているため、IDE（Eclipse / IntelliJ）に Lombok プラグインのインストールが必要です。

---

# 2. 起動方法

## 前提条件

以下のソフトウェアをインストールしてください。

- Java 17
- Maven 3.9
- MySQL 8.0
- MailHog（メール送信テスト用）
- Git

---

## 3. ソースコード取得

```bash
git clone -b main https://github.com/conan2000vip/HealthLogWebApp.git

cd HealthLogWebApp
```

---

## 4. データベース作成

MySQL に接続し、以下を実行してください。

```sql
CREATE DATABASE healthlog;
```

その後、以下の SQL ファイルを実行してください。

```
healthlog.sql
```

---

## 5. application.properties の設定

`src/main/resources/application.properties`

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/healthlog?useSSL=false&serverTimezone=UTC
spring.datasource.username=your_username
spring.datasource.password=your_password

spring.mail.host=localhost
spring.mail.port=1025

spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.starttls.enable=false

app.mail.from=no-reply@healthlog.local
app.frontend-url=http://localhost:8080
```

---

## 6. MailHog の起動（メール送信テスト）

MailHog を起動してください。

MailHog 起動後、ブラウザで以下へアクセスします。

```
http://localhost:8025
```

SMTP サーバー

```
localhost:1025
```

メール認証・パスワード再設定・認証メール再送信などのメールは、すべて MailHog 上で確認できます。

---

## 7. アプリケーション起動

```bash
mvn spring-boot:run
```

または Eclipse の場合

```
プロジェクトを右クリック
→ Run As
→ Spring Boot App
```

ブラウザ

```
http://localhost:8080
```

---

## 8. パスワードの取り扱い

GitHub にデータベースのパスワードなどの機密情報をコミットしないでください。

**NG**

```properties
spring.datasource.password=12345678
```

**OK**

```properties
spring.datasource.password=your_password
```

---

## 9. 動作確認

以下の機能について動作確認してください。

- ユーザー登録
- ログイン
- メール認証
- 認証メール再送信
- パスワード再設定
- プロフィール作成・切り替え
- 各健康記録画面

---

## 10. プロジェクト構成

```
HealthLogWebApp/
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── static/
│   │       └── templates/
│   └── test/
├── healthlog.sql
├── pom.xml
├── README.md
```

## 11. 関連ドキュメント
## ローカルメールテスト（MailHog）

本プロジェクトでは、ローカル環境でメール送信機能をテストするために **MailHog** を使用します。

### 1. MailHog のインストール

MailHog をダウンロードしてください。

### 2. MailHog の起動

アプリケーションを起動する前に、MailHog を実行してください。

- SMTP : `localhost:1025`
- Web UI : `http://localhost:8025`

ブラウザで以下のURLにアクセスすると、送信されたメールを確認できます。

```
http://localhost:8025
```

### 3. Spring Boot の設定

`application.properties`

```properties
spring.mail.host=localhost
spring.mail.port=1025

spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.starttls.enable=false

app.mail.from=no-reply@healthlog.local
app.frontend-url=http://localhost:8080
```

### 4. 動作確認

以下の機能を実行すると、送信されたメールが MailHog に表示されます。

- ユーザー登録
- メール認証
- パスワード再設定
- 認証メール再送信

