# HealthLogWebApp

健康管理（ヘルスログ）Webアプリケーション

アプリケーションに不具合が発生した場合、第三者（トレーナー等）がソースコードを取得し、同じ開発環境を再現できるように、本READMEおよびDB構築用SQLファイルを整備しています。

---

## 1. 開発環境

| 項目 | バージョン |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.16 |
| MySQL | 8.0（mysql-connector-j使用） |
| Maven | 3.9 |
| Docker / Docker Compose | 最新版 |


### 主な使用技術
* Spring Data JPA（DB操作）
* Thymeleaf（画面テンプレート）
* Spring Validation（入力チェック）
* Lombok（コード簡略化）
※ Lombok を使用しているため、IDE（Eclipse/IntelliJ）に Lombok プラグインのインストールが必要です。
---

## 2. 起動方法

起動方法は **Docker を使う方法（推奨）** と **ローカル環境で直接起動する方法** の2種類があります。

### 方法A：Docker を使う場合（推奨）

#### 前提条件
* Git
* Docker Desktop（Docker Compose 含む）

#### 手順

1. ソースコード取得
```bash
git clone -b main https://github.com/conan2000vip/HealthLogWebApp.git
cd HealthLogWebApp
```

2. 環境変数ファイルの準備
```bash
cp .env.example .env
```
`.env` の内容を自分の環境に合わせて修正してください（DBパスワード等）。

3. Docker イメージビルド
```bash
docker compose build
```

4. コンテナ起動
```bash
docker compose up -d
```

5. 起動確認
```bash
docker ps
```
`healthlog-mysql` と `healthlog-app` の2つのコンテナが `Up` になっていれば正常です。

6. ブラウザでアクセス
```
http://localhost:8080
```

※ より詳細な手順（トラブルシューティング含む）は `Docker.md` を参照してください。

---

### 方法B：ローカル環境で直接起動する場合

#### 前提条件
* Java 17
* Maven 3.9
* MySQL 8.0（ローカルまたはDockerで起動済みであること）

#### DB作成手順

MySQLに接続し、以下を実行してください。

```sql
CREATE DATABASE healthlog;
```

その後、以下のSQLファイルを実行してテーブルを作成してください。

```
healthlog.sql
```

#### アプリケーション設定

`src/main/resources/application.properties`（または `application-example.properties` をコピーして作成）に以下を設定してください。

```properties
spring.datasource.url=jdbc:mysql://localhost:3307/healthlog?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=your_password
```

※ 実際のパスワードはGitHubにコミットしないでください（詳細は「4. パスワードの取り扱い」参照）。

#### 起動

```bash
mvn spring-boot:run
```

または Eclipse / STS の場合：

```
プロジェクトを右クリック
→ Run As
→ Spring Boot App
```

---

## 3. DB構築用SQLファイル

データベース作成およびテーブル作成用SQLファイルは以下です。

```
healthlog.sql
```

MySQL Workbench 等で変更したテーブル定義は、変更のたびに本ファイルへ出力・更新してください。

---

## 4. パスワードの取り扱い

GitHubへソースコードをアップロードする際は、実際に使用しているパスワードをコミットしないでください。

**NG例**
```properties
spring.datasource.password=12345678
```

**OK例**
```properties
spring.datasource.password=your_password
```

実際のパスワードは各自の環境で `.env`（Docker利用時）または `application.properties`（ローカル起動時、Git管理外）に設定してください。`.env` および `application.properties` は `.gitignore` に含まれており、Gitにはコミットされません。設定項目のテンプレートは `.env.example` を参照してください。

---

## 5. 第三者による動作確認手順（トレーナー向け）

以下の手順で環境構築および動作確認を行うことができます。

① ソースコードを取得
```bash
git clone -b main https://github.com/conan2000vip/HealthLogWebApp.git
```

② 環境変数ファイルを準備
```bash
cp .env.example .env
```
`.env` にDBパスワード等、自分の環境に合わせた値を設定。

③ Dockerでビルド・起動
```bash
docker compose build
docker compose up -d
```

④ 動作確認
```
http://localhost:8080
```

⑤ 停止する場合
```bash
docker compose down
```

---

## 6. プロジェクト構成（概要）

```
HealthLogWebApp/
├── src/
│   ├── main/
│   │   ├── java/...          ← アプリケーションコード
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── healthlog.sql              ← DB構築用SQLファイル
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── Docker.md                  ← Docker詳細手順書
├── pom.xml
└── README.md
```

---

## 7. 関連ドキュメント

* [Docker.md](./Docker.md) — Docker環境構築の詳細手順（トラブルシューティング含む）
* [ブランチ計画](./ブランチ計画.docx) — 開発ブランチ運用ルール
