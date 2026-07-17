package com.healthlog.app.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

import com.healthlog.app.dto.LoginRequest;
import com.healthlog.app.dto.RegisterRequest;
import com.healthlog.app.entity.AuthToken;
import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.User;
import com.healthlog.app.repository.AuthTokenRepository;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.UserRepository;

@Service
public class AuthService {

	private static final int EMAIL_TOKEN_EXPIRY_MINUTES = 24 * 60;
	private static final int RESET_TOKEN_EXPIRY_MINUTES = 30;
	private static final int MAX_EMAIL_LENGTH = 255;

	private static final String TOKEN_TYPE_EMAIL_VERIFICATION = "email_verification";
	private static final String TOKEN_TYPE_PASSWORD_RESET = "password_reset";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
	private static final Pattern PASSWORD_PATTERN = Pattern
			.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#]).{8,}$");

	// パスワードポリシーに合致していても、よく使われるパスワードは拒否する
	private static final Set<String> WEAK_PASSWORDS = Set.of(
			"password", "password1", "12345678", "123456789", "qwerty123", "Password123!",
			"letmein1", "admin123", "welcome1", "iloveyou1", "abc12345");

	private final UserRepository userRepository;
	private final AuthTokenRepository authTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
	private final SecureRandom secureRandom = new SecureRandom();
	private final ProfileRepository profileRepository;

	public AuthService(UserRepository userRepository,
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

	//　register
	@Transactional
	// ユーザー登録処理（新規ユーザーの作成とメール認証トークンの発行）
	public void register(RegisterRequest request) {
		validateRegisterInput(request);
		if (userRepository.existsByEmail(request.getEmail())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "既に登録されています");
		}

		validatePasswordPolicy(request.getPassword());
		String email = request.getEmail().trim().toLowerCase();
		User user = new User();

		user.setAccountName(request.getAccountName());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		user.setEmailVerifiedAt(null);
		userRepository.save(user);
		userRepository.existsByEmail(email);

		Profile profile = new Profile();

		profile.setUser(user);
		profile.setName(user.getAccountName());
		profile.setRelationship("SELF");
		profile.setIsPrimary(true);
		profileRepository.save(profile);

		AuthToken verificationToken = issueAuthToken(user, TOKEN_TYPE_EMAIL_VERIFICATION,
				EMAIL_TOKEN_EXPIRY_MINUTES);
		emailService.sendVerificationEmail(user.getEmail(), verificationToken.getToken());
	}

	// 入力値の検証
	private void validateRegisterInput(RegisterRequest request) {
		if (request.getAccountName().length() > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名前は100文字以内で入力してください");
		}
		if (!StringUtils.hasText(request.getAccountName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名前を入力してください");
		}
		if (!StringUtils.hasText(request.getEmail())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスを入力してください");
		}
		if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスの形式が正しくありません");
		}
		if (request.getEmail().length() > MAX_EMAIL_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "255文字以内で入力してください");
		}
		if (!StringUtils.hasText(request.getPassword())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードを入力してください");
		}
	}

	// パスワードポリシーの検証
	private void validatePasswordPolicy(String password) {
		if (password.length() < 8) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "8文字以上で入力してください");
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
	public void login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		if (!StringUtils.hasText(request.getEmail())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスを入力してください");
		}
		if (!StringUtils.hasText(request.getPassword())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードを入力してください");
		}
		if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスの形式が正しくありません");
		}

		// ユーザー存在確認 401Error
		User user = userRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
						"メールアドレスまたはパスワードが正しくありません"));

		// パスワード照合 401Error
		if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません");
		}

		// メール認証済みか確認 403Error
		if (user.getEmailVerifiedAt() == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "メール認証を完了してください");
		}

		// ログイン成功 -> Spring SecurityのSecurityContextに認証情報を設定
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				user.getEmail(),
				null,
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		// セッションにSecurityContextを保存（HttpSessionSecurityContextRepositoryを使用）
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
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

		// トークンの存在確認
		AuthToken authToken = authTokenRepository
				.findByTokenAndTokenType(token, TOKEN_TYPE_EMAIL_VERIFICATION)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンが無効です"));

		// トークンの使用済みフラグと有効期限を確認
		if (Boolean.TRUE.equals(authToken.getUsedFlg())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンが無効です");
		}
		if (authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "トークンの有効期限が切れています");
		}

		// メール認証済みフラグを更新
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
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスの形式が正しくありません");
		}

		//メールアドレスが登録されていない場合でも、セキュリティ上の理由から成功として返す
		userRepository.findByEmail(email).ifPresent(user -> {
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
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスの形式が正しくありません");
		}

		//メールアドレスが登録されていない場合でも、セキュリティ上の理由から成功として返す
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

		if (!StringUtils.hasText(newPassword)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードを入力してください");
		}

		User user = authToken.getUser();

		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "現在使用中のパスワードは指定できません");
		}

		validatePasswordPolicy(newPassword);

		user.setPasswordHash(passwordEncoder.encode(newPassword));
		userRepository.save(user);

		authToken.setUsedFlg(true);
		authTokenRepository.save(authToken);
	}

	// helpers
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
}