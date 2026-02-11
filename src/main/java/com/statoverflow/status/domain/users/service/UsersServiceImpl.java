package com.statoverflow.status.domain.users.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.statoverflow.status.domain.attribute.dto.AttributesReturnDto;
import com.statoverflow.status.domain.attribute.repository.AttributeRepository;
import com.statoverflow.status.domain.attribute.repository.UsersAttributeProgressRepository;
import com.statoverflow.status.domain.attribute.service.AttributeService;
import com.statoverflow.status.domain.auth.dto.OAuthLoginRequestDto;
import com.statoverflow.status.domain.auth.dto.OAuthProviderDto;
import com.statoverflow.status.domain.auth.dto.SignUpRequestDto;
import com.statoverflow.status.domain.auth.dto.SocialLoginReturnDto;
import com.statoverflow.status.domain.master.entity.Attribute;
import com.statoverflow.status.domain.master.entity.NicknameGenerator;
import com.statoverflow.status.domain.master.entity.TermsAndConditions;
import com.statoverflow.status.domain.master.entity.TierLevel;
import com.statoverflow.status.domain.master.enums.DefaultNicknameType;
import com.statoverflow.status.domain.master.enums.TermsType;
import com.statoverflow.status.domain.master.repository.NicknameGeneratorRepository;
import com.statoverflow.status.domain.users.dto.BasicUsersDto;
import com.statoverflow.status.domain.users.dto.TierDto;
import com.statoverflow.status.domain.users.dto.WithTier;
import com.statoverflow.status.domain.users.entity.UsersAgreements;
import com.statoverflow.status.domain.users.enums.ProviderType;
import com.statoverflow.status.domain.users.repository.TermsAndConditionsRepository;
import com.statoverflow.status.domain.users.entity.Users;
import com.statoverflow.status.domain.users.entity.UsersAttributeProgress;
import com.statoverflow.status.domain.users.enums.AccountStatus;
import com.statoverflow.status.domain.users.repository.TierLevelRepository;
import com.statoverflow.status.domain.users.repository.UsersAgreementsRepository;
import com.statoverflow.status.domain.users.repository.UsersRepository;
import com.statoverflow.status.global.error.ErrorType;
import com.statoverflow.status.global.exception.CustomException;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class UsersServiceImpl implements UsersService{

	private final UsersRepository usersRepository;
	private final UsersAttributeProgressRepository	usersAttributeProgressRepository;
	private final AttributeRepository attributeRepository;
	private final NicknameGeneratorRepository nicknameGeneratorRepository;
	private final Random random;

	private final AttributeService attributeService;
	private final TierLevelRepository tierLevelRepository;

	@Value("${status.users.users-service.characters}")
	private String VALID_CHARACTERS;

	@Value("${status.users.users-service.length}")
	private int TAG_LENGTH;

	private final TermsAndConditionsRepository termsAndConditionsRepository;
	private final UsersAgreementsRepository usersAgreementsRepository;

	@Override
	public SocialLoginReturnDto getUsersByProvider(OAuthProviderDto provider) {
		return usersRepository.findByProviderTypeAndProviderId(
				provider.providerType(), provider.providerId()
			)
			.map(user -> {
				TierDto tier = getTier(user.getId());
				return (SocialLoginReturnDto) BasicUsersDto.from(user, tier);
			})
			.orElse(provider);
	}

	@Override
	public BasicUsersDto signUp() {
		String nickname = generateRandomNickname();
		String tag = generateTagForNickname(nickname);
		Users user = Users.builder()
			.nickname(nickname)
			.tag(tag)
			.providerType(ProviderType.GUEST)
			.providerId(tag)
			.build();

		usersRepository.save(user);

		initializeUserAttributes(user);

		agreeToLatestRequiredTerms(user);

		log.debug("회원가입 완료: {}", user.getId());
		return BasicUsersDto.from(user, getTier(user.getId()));
	}

	private String generateRandomNickname() {
		List<NicknameGenerator> adjectives = nicknameGeneratorRepository.findAllByType(DefaultNicknameType.ADJECTIVE);
		List<NicknameGenerator> nouns = nicknameGeneratorRepository.findAllByType(DefaultNicknameType.NOUN);

		// 랜덤 인덱스를 생성하여 하나씩 선택
		String randomAdjective = adjectives.get(random.nextInt(adjectives.size())).getName();
		String randomNoun = nouns.get(random.nextInt(nouns.size())).getName();

		log.debug("랜덤 닉네임 생성 완료, 닉네임 : {}", randomAdjective + randomNoun);

		// 닉네임 생성
		return randomAdjective + randomNoun;
	}

	@Override
	public BasicUsersDto signUp(SignUpRequestDto req) {

		log.debug("회원가입 시작, req: {}", req);

		// 중복 가입 방지
		usersRepository.findByProviderTypeAndProviderId(
			req.provider().providerType(), req.provider().providerId()
		).ifPresent(existingUser -> {
			log.warn("이미 가입된 사용자 시도: provider={}, providerId={}", 
				req.provider().providerType(), req.provider().providerId());
			throw new CustomException(ErrorType.SOCIAL_ALREADY_CONNECTED);
		});

		Users user = req.toEntity();

		// 닉네임에 고유  Tag 생성
		String tag = generateTagForNickname(req.nickname());
		log.debug("닉네임에 따른 랜덤 Tag 생성: {}", tag);
		user.setTag(tag);

		usersRepository.save(user);

		// 모든 마스터 Attribute에 대해 초기 UsersAttributeProgress 생성 및 저장
		initializeUserAttributes(user);

		agreeToLatestRequiredTerms(user);

		log.debug("회원가입 완료: {}", user.getId());
		return BasicUsersDto.from(user, getTier(user.getId()));
	}

	private void agreeToLatestRequiredTerms(Users user) {
		// 현재 유효한 모든 필수 약관의 최신 버전을 조회
		List<TermsAndConditions> validTerms = termsAndConditionsRepository.findAllLatestEssentialEffectiveByEachType(
			LocalDate.now());

		List<UsersAgreements> usersAgreements = new ArrayList<>();
		validTerms.forEach(
			termsAndConditions -> {

				if((user.getProviderType().equals(ProviderType.GUEST) && termsAndConditions.getProviderType().equals(ProviderType.LoginType.GUEST) ||
					(! user.getProviderType().equals(ProviderType.GUEST)) && termsAndConditions.getProviderType().equals(ProviderType.LoginType.SOCIAL))) {
					usersAgreements.add(UsersAgreements.builder()
						.user(user)
						.terms(termsAndConditions)
						.build());
				}


			}
		);
		usersAgreementsRepository.saveAll(usersAgreements);

		log.debug("사용자 {}가 최신 필수 약관 {} 개에 동의 처리되었습니다.", user.getId(), usersAgreements.size());

	}

	@Override
	public BasicUsersDto updateNickname(Long userId, String nickname) {
		Users user = usersRepository.findById(userId)
			.orElseThrow(() -> new CustomException(ErrorType.RESOURCE_NOT_FOUND));

		if(user.getNickname() != null && user.getNickname().equals(nickname)) {
			throw new CustomException(ErrorType.NICKNAME_NOT_CHANGED);
		}

		user.setTag(generateTagForNickname(nickname));
		user.setNickname(nickname);

		return BasicUsersDto.from(user, getTier(user.getId()));
	}

	@Override
	public void deleteUser(Long id, HttpServletResponse response) {
		// providerId 날리기
		Users user = usersRepository.findById(id).orElseThrow(() -> new CustomException(ErrorType.RESOURCE_NOT_FOUND));
		user.setStatus(AccountStatus.INACTIVE);
		user.setProviderId("");
		usersRepository.save(user);
	}

	@Override
	public BasicUsersDto connectProvider(BasicUsersDto users, OAuthProviderDto req) {
		Users user = usersRepository.findByIdAndProviderType(users.id(), ProviderType.GUEST)
			.orElseThrow(() -> new CustomException(ErrorType.RESOURCE_NOT_FOUND));

		SocialLoginReturnDto res = getUsersByProvider(req);

		if(! res.type().equals("SIGNUP")) {
            return (BasicUsersDto) res;
		}

		user.setProviderType(req.providerType());
		user.setProviderId(req.providerId());

		agreeToLatestRequiredTerms(user);

		return BasicUsersDto.from(user, getTier(user.getId()));
	}

	private void initializeUserAttributes(Users user) {
		List<Attribute> allAttributes = attributeRepository.findAll();

		List<UsersAttributeProgress> initialProgresses = allAttributes.stream()
				.map(attribute -> UsersAttributeProgress.builder()
						.user(user)
						.attribute(attribute)
						.build())
				.collect(Collectors.toList());

		usersAttributeProgressRepository.saveAll(initialProgresses);
	}

	private String generateTagForNickname(String nickname) {
		String generatedTag;
		boolean isDuplicate;
		SecureRandom random = new SecureRandom(); // 보안적으로 강력한 난수 생성기

		int charactersLength = VALID_CHARACTERS.length();

		// 무작위로 태그를 생성하고 중복을 확인
		do {
			StringBuilder tagBuilder = new StringBuilder();
			for (int i = 0; i < TAG_LENGTH; i++) {
				tagBuilder.append(VALID_CHARACTERS.charAt(random.nextInt(charactersLength)));
			}
			generatedTag = tagBuilder.toString();

			isDuplicate = usersRepository.existsByNicknameAndTag(nickname, generatedTag);

		} while (isDuplicate); // 중복이 발생하면 다시 생성

		return generatedTag;
	}

	@Override
	public TierDto getTier(Long userId) {
		int levelSum = attributeService.getAttributes(userId)
			.stream()
			.mapToInt(AttributesReturnDto::level)
			.sum();
		levelSum -= 12;
		TierLevel tierLevel = tierLevelRepository.findTopByXpRequiredGreaterThanOrderByXpRequiredAsc((long)levelSum);

		return new TierDto(tierLevel.getGrade(), tierLevel.getLevelOutput());
	}
}
