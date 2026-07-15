# Docker開発環境 手順書（詳細版）

本手順書はGitから取得後、各メンバーがローカルでアプリを起動するまでの手順を具体的かつ詳細にまとめたものです。

## 1. 前提条件

以下がローカルPCにインストールされていること。

* Git
* Docker Desktop（Docker / Docker Compose）

バージョン確認：

```bash
git --version
docker --version
docker compose version
```
### 1.1 Docker のインストール(Docker のインストールした方はスキップしてください。 )

* インストールリンク：[https://www.docker.com/ja-jp/](https://www.docker.com/ja-jp/)
* 選択案内：
環境によって
  * Mac 版 (Apple Silicon)  → M1/M2搭載 Mac 用
  * Mac 版 (Intel)         → Intel搭載 Mac 用
  * Windows 版 (AMD64)     → Intel/AMD CPU 搭載 Windows PC 用
  * Windows 版 (ARM64)     → ARM CPU 搭載 Windows PC 用
  * Linux 版               → Linux 用

※ Docker Desktop インストール後、Docker Compose も含まれています。
Linuxの場合は以下を参考に手動インストールしてください。

```bash
sudo apt update
sudo apt install docker-compose-plugin
docker compose version
```
## 2. ソースコード取得

​```bash
cd your-project(保存したい場所)
git clone -b main https://github.com/conan2000vip/HealthLogWebApp.git
​```

* フォルダ構成確認(powershell)
* `.env.example` がプロジェクト直下にあることを確認
​```bash
ls
​```

## 3. 環境変数ファイルの準備

* 無い場合は `.env.example` からコピーして作成

```bash
cp .env.example .env
```
* `.env` の内容は各自の環境に合わせて修正（DBパスワード等）

## 4. Docker イメージをビルドする
* docker 起動する

```bash
docker compose build
```
* Dockerfile を元にアプリ用イメージを作成
* 正常終了時には `Successfully built <イメージID>` と表示
* エラー例：`docker-compose.yml not found` → カレントディレクトリ確認

```bash
ls
cd "C:\pleiades\workspace\HealthLogWebApp"
```

* 再実行

```bash
docker compose build
```
* ポート番号 をローカルに占用された場合のエラー発生した場合は.envファイルに指定したポート番号が使えあれているため、別のポート番号を指定直してください。

## 5. コンテナ起動

```bash
docker compose up -d
```

* `-d` はバックグラウンド実行
* 正常時、コンテナが起動し、端末には `Starting <サービス名>` が表示
* エラー例：ポート競合 → 使用中ポート確認、変更または既存コンテナ停止

```bash
docker ps
docker stop <コンテナID>
```

## 6. コンテナ状態確認

```bash
docker ps
```

* STATUS が `Up` になっていれば正常
* 停止している場合は

```bash
docker ps -a
```

## 7. コンテナログ確認（必要な場合）

```bash
docker logs <コンテナIDまたは名前>
```

* 正常ログ：アプリ起動メッセージ
* エラーログ：例）DB接続失敗 → 設定・環境変数を確認

## 8. アプリ確認

* ブラウザでアクセス

```
http://localhost:8080
```

* 正常に画面が表示されることを確認
* 表示されない場合は、`docker ps` と `docker logs` で確認

## 9. DB接続確認（必要な場合）

* DBが正しく接続しているかを確認したい場合：
* DBに接続
```
docker exec -it healthlog-mysql mysql -u root -p
Enter password:
```
* DB確認
```
SHOW DATABASES;
USE <mydb_name>;
SHOW TABLES; 

//テーベル確認する
DESC <table_name>;
SHOW CREATE TABLE <table_name>;
```

## 10. 停止・再起動

### 10.1 停止

```bash
docker compose down
```
### 10.2 再起動

```bash
docker compose up -d
```
* 必要に応じて `-v` オプションでボリューム削除（DBリセット）

```bash
docker compose down -v
```
