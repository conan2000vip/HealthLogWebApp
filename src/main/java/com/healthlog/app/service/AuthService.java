package com.healthlog.app.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.healthlog.app.entity.AuthToken;
import com.healthlog.app.entity.User;
import com.healthlog.app.repository.AuthTokenRepository;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.UserRepository;

@Service
public class AuthService {

	private static final int EMAIL_TOKEN_EXPIRY_MINUTES = 24 * 60;
	private static final int RESET_TOKEN_EXPIRY_MINUTES = 30;
	private static final int MAX_EMAIL_LENGTH = 255;
	private static final int MAX_PASSWORD_LENGTH = 100;
	private static final String TOKEN_TYPE_EMAIL_VERIFICATION = "email_verification";
	private static final String TOKEN_TYPE_PASSWORD_RESET = "password_reset";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
	private static final Pattern PASSWORD_PATTERN = Pattern
			// @, $, !, %, \*, ?, #, &
			.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#]).{8,}$");

	private static final Set<String> WEAK_PASSWORDS = Set.of(
			"password", "password1", "12345678", "123456789", "qwerty123", "Password123!",
			"letmein1", "admin123", "welcome1", "iloveyou1", "abc12345");

	private final UserRepository userRepository;
	private final ProfileRepository profileRepository;
	private final AuthTokenRepository authTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(
			UserRepository userRepository,
			ProfileRepository profileRepository,
			AuthTokenRepository authTokenRepository,
			PasswordEncoder passwordEncoder,
			EmailService emailService) {
		this.userRepository = userRepository;
		this.authTokenRepository = authTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailService = emailService;
		this.profileRepository = profileRepository;
	}

	// register
	// ユーザー登録処理（新規ユーザーの作成とメール認証トークンの発行）
	@Transactional
	public void register(
			String accountName,
			String email,
			String password) {

		validateAccountName(accountName);
		validateEmail(email);
		validatePasswordPolicy(password);
		email = email.trim().toLowerCase();
		if (userRepository.existsByEmail(email)) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"メールが既に登録されています");
		}

		User user = new User();
		user.setAccountName(accountName.trim());
		user.setEmail(email);
		user.setPasswordHash(
				passwordEncoder.encode(password));

		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"メールが既に登録されています");
		}

		AuthToken token = issueAuthToken(
				user,
				TOKEN_TYPE_EMAIL_VERIFICATION,
				EMAIL_TOKEN_EXPIRY_MINUTES);
		emailService.sendVerificationEmail(
				user.getEmail(),
				token.getToken());
	}

	private void validateAccountName(String accountName) {
		if (!StringUtils.hasText(accountName)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"名前を入力してください");
		}
		String value = accountName.trim();
		if (value.length() < 3 || value.length() > 50) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"名前は3〜50文字で入力してください");
		}
		if (!value.matches("^[a-zA-Z0-9_ぁ-んァ-ヶー一-龯]+$")) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"名前は英数字、アンダースコア、日本語のみ使用できます");
		}
	}

	private void validateEmail(String email) {
		if (email == null || email.trim().isEmpty()) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"メールを入力してください");
		}
		if (email.length() > MAX_EMAIL_LENGTH) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"メールアドレスが長すぎます");
		}
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"メールアドレスの形式が正しくありません");
		}
	}

	// パスワードポリシーの検証（登録・リセット共通で使用する唯一の検証ロジック）
	private void validatePasswordPolicy(String password) {
		if (!StringUtils.hasText(password)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"パスワードを入力してください");
		}
		if (password.length() > MAX_PASSWORD_LENGTH) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"パスワードは100文字以内で入力してください");
		}
		if (!PASSWORD_PATTERN.matcher(password).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"大文字・小文字・数字・特殊記号をそれぞれ1文字以上含めてください");
		}
		if (WEAK_PASSWORDS.contains(password.toLowerCase())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "このパスワードは一般的すぎて使用できません");
		}
	}

	// login
	@Transactional
	// ログイン処理（認証済みユーザーのセッションを作成）
	public void login(
			String email,
			String password,
			HttpServletRequest request,
			HttpServletResponse response) {
		if (!StringUtils.hasText(email)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"メールアドレスを入力してください");
		}
		if (!StringUtils.hasText(password)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"パスワードを入力してください");
		}
		email = email.trim().toLowerCase();
		User user = userRepository.findByEmail(email)
				.orElseThrow(
						() -> new ResponseStatusException(
								HttpStatus.UNAUTHORIZED,
								"メールアドレスまたはパスワードが正しくありません"));
		if (!passwordEncoder.matches(
				password,
				user.getPasswordHash())) {
			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED,
					"メールアドレスまたはパスワードが正しくありません");
		}

		if (user.getEmailVerifiedAt() == null) {
			throw new ResponseStatusException(
					HttpStatus.FORBIDDEN,
					"メール認証を完了してください");
		}

		Authentication authentication = new UsernamePasswordAuthenticationToken(
				user.getEmail(),
				null,
				Collections.singletonList(
						new SimpleGrantedAuthority(
								"ROLE_USER")));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(
				context,
				request,
				response);
	}

	// logout
	@Transactional
	// ログアウト処理（セッションの破棄）
	public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		// セッションの破棄とSecurityContextのクリア
		new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
	}

	// verifyEmail
	@Transactional
	// メール認証処理（トークンの検証とユーザーのメール認証状態の更新）
	public void verifyEmail(String token) {
		if (!StringUtils.hasText(token)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンが指定されていません");
		}

		AuthToken authToken = authTokenRepository
				.findByTokenAndTokenType(token, TOKEN_TYPE_EMAIL_VERIFICATION)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンが無効です"));

		if (Boolean.TRUE.equals(authToken.getUsedFlg())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンが無効です");
		}
		if (authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンの有効期限が切れています");
		}

		User user = authToken.getUser();
		user.setEmailVerifiedAt(LocalDateTime.now());
		userRepository.save(user);

		authToken.setUsedFlg(true);
		authTokenRepository.save(authToken);
	}

	// resendVerification
	@Transactional
	// メール認証トークンの再発行処理（既存の未使用トークンを無効化して新しいトークンを発行）
	public void resendVerification(String email) {
		if (!StringUtils.hasText(email)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスを入力してください");
		}
		email = email.trim().toLowerCase();
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスの形式が正しくありません");
		}

		// メールアドレスが登録されていない場合、または既にメール認証済みの場合でも
		// セキュリティ上の理由から常に成功として返す（実際の送信のみスキップする）
		userRepository.findByEmail(email).ifPresent(user -> {
			if (user.getEmailVerifiedAt() != null) {
				return; // 既に認証済みなら何もしない
			}
			authTokenRepository.invalidateActiveTokens(user.getId(), TOKEN_TYPE_EMAIL_VERIFICATION);

			AuthToken newToken = issueAuthToken(user, TOKEN_TYPE_EMAIL_VERIFICATION,
					EMAIL_TOKEN_EXPIRY_MINUTES);
			emailService.sendVerificationEmail(user.getEmail(), newToken.getToken());
		});
	}

	// requestPasswordReset
	@Transactional
	// パスワードリセット要求処理（既存の未使用トークンを無効化して新しいトークンを発行）
	public void requestPasswordReset(String email) {
		if (!StringUtils.hasText(email)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスを入力してください");
		}
		email = email.trim().toLowerCase();
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスの形式が正しくありません");
		}

		// メールアドレスが登録されていない場合でも、セキュリティ上の理由から成功として返す
		userRepository.findByEmail(email).ifPresent(user -> {
			authTokenRepository.invalidateActiveTokens(user.getId(), TOKEN_TYPE_PASSWORD_RESET);

			AuthToken resetToken = issueAuthToken(user, TOKEN_TYPE_PASSWORD_RESET,
					RESET_TOKEN_EXPIRY_MINUTES);
			emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());
		});
	}

	// confirmPasswordReset
	@Transactional
	// パスワードリセット確認処理（トークンの検証と新しいパスワードの設定）
	public void confirmPasswordReset(String token, String newPassword) {
		if (!StringUtils.hasText(token)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンが指定されていません");
		}
		AuthToken authToken = authTokenRepository
				.findByTokenAndTokenType(token, TOKEN_TYPE_PASSWORD_RESET)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンが無効です"));

		if (Boolean.TRUE.equals(authToken.getUsedFlg())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンが無効です");
		}
		if (authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンの有効期限が切れています");
		}

		// パスワード形式を先に検証（不正な形式のまま次に進まない）
		validatePasswordPolicy(newPassword);
		User user = authToken.getUser();
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "現在使用中のパスワードは指定できません");
		}
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		authToken.setUsedFlg(true);
		authTokenRepository.save(authToken);
	}

	// 認証トークンの発行処理（トークンの生成と保存）
	private AuthToken issueAuthToken(User user, String tokenType, long expiryMinutes) {
		AuthToken authToken = new AuthToken();
		authToken.setUser(user);
		authToken.setToken(generateUniqueToken());
		authToken.setTokenType(tokenType);
		authToken.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
		authToken.setUsedFlg(false);
		return authTokenRepository.save(authToken);
	}

	private String generateSecureToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String generateUniqueToken() {
		String token = generateSecureToken();
		while (authTokenRepository.existsByToken(token)) {
			token = generateSecureToken();
		}
		return token;
	}

	public User getCurrentUser() {
		Authentication authentication = SecurityContextHolder
				.getContext()
				.getAuthentication();
		String email = authentication.getName();
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND,
						"ユーザーが見つかりません"));
	}
}