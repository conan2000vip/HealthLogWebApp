package com.healthlog.app.constant;

// セッション用定数クラスのインスタンス生成を防止
public final class SessionConstants {

	// 現在のプロフィールIDをセッションに保存するためのキー
	public static final String CURRENT_PROFILE_ID = "CURRENT_PROFILE_ID";

	// プロフィールの作成完了メッセージをセッションに保存するためのキー
	private SessionConstants() {
	}
}