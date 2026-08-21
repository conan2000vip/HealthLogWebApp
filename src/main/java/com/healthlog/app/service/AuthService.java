package com.healthlog.app.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
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

import com.healthlog.app.entity.AuthToken;
import com.healthlog.app.entity.User;
import com.healthlog.app.exception.BusinessException;
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
	private static final int RESEND_COOLDOWN_SECONDS = 60;

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
	private static final Pattern PASSWORD_PATTERN = Pattern
			// @, $, !, %, \*, ?, #, &
			.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#]).{8,}$");

	private static final Set<String> WEAK_PASSWORDS = Set.of("qwerty123!", "admin123!", "welcome123!", "password1!",
			"password123!", "welcome1!", "iloveyou1!", "abc12345!");

	private final UserRepository userRepository;
	private final AuthTokenRepository authTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(UserRepository userRepository, ProfileRepository profileRepository,
			AuthTokenRepository authTokenRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
		this.userRepository = userRepository;
		this.authTokenRepository = authTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailService = emailService;
	}

	// register
	// ユーザー登録処理（新規ユーザーの作成とメール認証トークンの発行）
	@Transactional
	public void register(String email, String password) {

		validateEmail(email);
		validatePasswordPolicy(password, "password");
		email = email.trim().toLowerCase();
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(HttpStatus.CONFLICT, "email", "メールが既に登録されています");
		}

		User user = new User();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(password));

		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(HttpStatus.CONFLICT, "email", "メールが既に登録されています");
		}

		AuthToken token = issueAuthToken(user, TOKEN_TYPE_EMAIL_VERIFICATION, EMAIL_TOKEN_EXPIRY_MINUTES);
		emailService.sendVerificationEmail(user.getEmail(), token.getToken());
	}

	private void validateEmail(String email) {
		if (email == null || email.trim().isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "email", "メールを入力してください");
		}
		if (email.length() > MAX_EMAIL_LENGTH) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "email", "メールアドレスが長すぎます");
		}
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "email", "メールアドレスの形式が正しくありません");
		}
	}

	private void validatePasswordPolicy(String password, String fieldName) {
		if (!StringUtils.hasText(password)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, fieldName, "パスワードを入力してください");
		}
		if (password.length() > MAX_PASSWORD_LENGTH) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, fieldName, "パスワードは100文字以内で入力してください");
		}
		if (!PASSWORD_PATTERN.matcher(password).matches()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, fieldName, "大文字・小文字・数字・特殊記号をそれぞれ1文字以上含めてください");
		}
		if (WEAK_PASSWORDS.contains(password.toLowerCase())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, fieldName, "このパスワードは一般的すぎて使用できません");
		}
	}

	// login
	@Transactional
	// ログイン処理（認証済みユーザーのセッションを作成）
	public void login(String email, String password, HttpServletRequest request, HttpServletResponse response) {
		if (!StringUtils.hasText(email)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メールアドレスを入力してください");
		}
		if (!StringUtils.hasText(password)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "パスワードを入力してください");
		}
		email = email.trim().toLowerCase();
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません"));
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new BusinessException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません");
		}

		if (user.getEmailVerifiedAt() == null) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "メール認証を完了してください");
		}

		Authentication authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null,
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
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
			throw new BusinessException(HttpStatus.BAD_REQUEST, "リンクが指定されていません");
		}

		AuthToken authToken = authTokenRepository.findByTokenAndTokenType(token, TOKEN_TYPE_EMAIL_VERIFICATION)
				.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "リンクが無効です"));

		if (Boolean.TRUE.equals(authToken.getUsedFlg())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "リンクが無効です");
		}
		if (authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "リンクの有効期限が切れています");
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
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メールアドレスを入力してください");
		}
		email = email.trim().toLowerCase();
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メールアドレスの形式が正しくありません");
		}
		// メールアドレスが登録されていない場合、または既にメール認証済みの場合でも
		// セキュリティ上の理由から常に成功として返す（実際の送信のみスキップする）
		userRepository.findByEmail(email).ifPresent(user -> {
			if (user.getEmailVerifiedAt() != null) {
				return; // 既に認証済みなら何もしない
			}
			// 直近のトークン発行から一定時間内（クールダウン中）であれば再送をスキップ
			// ※ 列挙攻撃対策のため、呼び出し元には常に同一の成功メッセージを返す
			Optional<AuthToken> latestToken = authTokenRepository
					.findFirstByUser_IdAndTokenTypeOrderByCreatedAtDesc(user.getId(), TOKEN_TYPE_EMAIL_VERIFICATION);

			if (latestToken.isPresent() && latestToken.get().getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS)
					.isAfter(LocalDateTime.now())) {
				return; // クールダウン中のため送信スキップ
			}
			authTokenRepository.invalidateActiveTokens(user.getId(), TOKEN_TYPE_EMAIL_VERIFICATION);
			AuthToken newToken = issueAuthToken(user, TOKEN_TYPE_EMAIL_VERIFICATION, EMAIL_TOKEN_EXPIRY_MINUTES);
			emailService.sendVerificationEmail(user.getEmail(), newToken.getToken());
		});
	}

	// requestPasswordReset
	@Transactional
	// パスワードリセット要求処理（既存の未使用トークンを無効化して新しいトークンを発行）
	public void requestPasswordReset(String email) {
		if (!StringUtils.hasText(email)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メールアドレスを入力してください");
		}
		email = email.trim().toLowerCase();
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メールアドレスの形式が正しくありません");
		}

		// メールアドレスが登録されていない場合でも、セキュリティ上の理由から成功として返す
		userRepository.findByEmail(email).ifPresent(user -> {
			authTokenRepository.invalidateActiveTokens(user.getId(), TOKEN_TYPE_PASSWORD_RESET);

			AuthToken resetToken = issueAuthToken(user, TOKEN_TYPE_PASSWORD_RESET, RESET_TOKEN_EXPIRY_MINUTES);
			emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());
		});
	}

	// confirmPasswordReset
	@Transactional
	// パスワードリセット確認処理（トークンの検証と新しいパスワードの設定）
	public void confirmPasswordReset(String token, String newPassword) {
		if (!StringUtils.hasText(token)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "リンクが指定されていません");
		}
		AuthToken authToken = authTokenRepository.findByTokenAndTokenType(token, TOKEN_TYPE_PASSWORD_RESET)
				.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "リンクが無効です"));

		if (Boolean.TRUE.equals(authToken.getUsedFlg())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "リンクが無効です");
		}
		if (authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "リンクの有効期限が切れています");
		}

		// パスワード形式を先に検証（不正な形式のまま次に進まない）
		validatePasswordPolicy(newPassword, "newPassword");
		User user = authToken.getUser();
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
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));
	}
}