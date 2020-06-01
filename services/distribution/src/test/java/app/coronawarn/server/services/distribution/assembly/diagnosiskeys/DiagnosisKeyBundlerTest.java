package app.coronawarn.server.services.distribution.assembly.diagnosiskeys;

import static org.assertj.core.api.Assertions.assertThat;

import app.coronawarn.server.common.persistence.domain.DiagnosisKey;
import app.coronawarn.server.services.distribution.config.DistributionServiceConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@EnableConfigurationProperties(value = DistributionServiceConfig.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DistributionServiceConfig.class},
    initializers = ConfigFileApplicationContextInitializer.class)
class DiagnosisKeyBundlerTest {

  @Autowired
  DistributionServiceConfig distributionServiceConfig;

  @Test
  void testEmptyListWhenNoDiagnosisKeys() {
    DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(List.of(), distributionServiceConfig);
    assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 1, 0, 0, 0))).hasSize(0);
  }

  @Test
  void testEmptyListWhenGettingDistributableKeysBeforeEarliestDiagnosisKey() {
    List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(6, 50L, 5);
    DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
    assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 1, 0, 0, 0))).hasSize(0);
  }

  @Nested
  @DisplayName("Expiry policy")
  class DiagnosisKeyBundlerExpiryPolicyTest {

    @ParameterizedTest
    @ValueSource(longs = {0L, 24L, 24L + 2L})
    void testLastPeriodOfHourAndSubmissionLessThanDistributionDateTime(long submissionTimestamp) {
      List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(5, submissionTimestamp, 10);
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 2, 3, 0, 0))).hasSize(10);
    }

    @Test
    void testLastPeriodOfHourAndSubmissionEqualsDistributionDateTime() {
      List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(5, 24L + 3L, 10);
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 2, 3, 0, 0))).hasSize(10);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 24L, 24L + 2L, 24L + 3L})
    void testFirstPeriodOfHourAndSubmissionLessThanDistributionDateTime(long submissionTimestamp) {
      List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(6, submissionTimestamp, 10);
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 2, 4, 0, 0))).hasSize(10);
    }

    @Test
    void testFirstPeriodOfHourAndSubmissionEqualsDistributionDateTime() {
      List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(6, 24L + 4L, 10);
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 2, 4, 0, 0))).hasSize(10);
    }

    @Test
    void testLastPeriodOfHourAndSubmissionGreaterDistributionDateTime() {
      List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(5, 24L + 4L, 10);
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 2, 4, 0, 0))).hasSize(10);
    }
  }

  @Nested
  @DisplayName("Shifting policy")
  class DiagnosisKeyBundlerShiftingPolicyTest {

    @Test
    void testDoesNotShiftIfPackageSizeGreaterThanThreshold() {
      List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(6, 50L, 6);
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 2, 0, 0))).hasSize(6);
    }

    @Test
    void testDoesNotShiftIfPackageSizeEqualsThreshold() {
      List<DiagnosisKey> diagnosisKeys = buildDiagnosisKeys(6, 50L, 5);
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 2, 0, 0))).hasSize(5);
    }

    @Test
    void testShiftsIfPackageSizeLessThanThreshold() {
      List<DiagnosisKey> diagnosisKeys = Stream
          .concat(buildDiagnosisKeys(6, 50L, 4).stream(), buildDiagnosisKeys(6, 51L, 1).stream())
          .collect(Collectors.toList());
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 2, 0, 0))).hasSize(0);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 3, 0, 0))).hasSize(5);
    }

    @Test
    void testShiftsSinceLastDistribution() {
      List<DiagnosisKey> diagnosisKeys = Stream
          .of(buildDiagnosisKeys(6, 50L, 5), buildDiagnosisKeys(6, 51L, 2), buildDiagnosisKeys(6, 52L, 4))
          .flatMap(List::stream)
          .collect(Collectors.toList());
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 2, 0, 0))).hasSize(5);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 3, 0, 0))).hasSize(0);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 4, 0, 0))).hasSize(6);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 5, 0, 0))).hasSize(0);
    }

    @Test
    void testShiftIncludesPreviouslyUndistributedKeys() {
      List<DiagnosisKey> diagnosisKeys = Stream
          .concat(buildDiagnosisKeys(6, 50L, 1).stream(), buildDiagnosisKeys(6, 51L, 5).stream())
          .collect(Collectors.toList());
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 2, 0, 0))).hasSize(0);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 3, 0, 0))).hasSize(5);
    }

    @Test
    void testShiftsSparseDistributions() {
      List<DiagnosisKey> diagnosisKeys = Stream
          .of(buildDiagnosisKeys(6, 50L, 1), buildDiagnosisKeys(6, 51L, 1), buildDiagnosisKeys(6, 52L, 1),
              buildDiagnosisKeys(6, 53L, 0), buildDiagnosisKeys(6, 54L, 0), buildDiagnosisKeys(6, 55L, 1),
              buildDiagnosisKeys(6, 56L, 1))
          .flatMap(List::stream)
          .collect(Collectors.toList());
      DiagnosisKeyBundler bundler = new DiagnosisKeyBundler(diagnosisKeys, distributionServiceConfig);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 2, 0, 0))).hasSize(0);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 3, 0, 0))).hasSize(0);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 4, 0, 0))).hasSize(0);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 5, 0, 0))).hasSize(0);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 6, 0, 0))).hasSize(0);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 7, 0, 0))).hasSize(0);
      assertThat(bundler.getDiagnosisKeysDistributableAt(LocalDateTime.of(1970, 1, 3, 8, 0, 0))).hasSize(5);
    }
  }

  public List<DiagnosisKey> buildDiagnosisKeys(int startIntervalNumber, long submissionTimestamp, int number) {
    return IntStream.range(0, number)
        .mapToObj(__ -> DiagnosisKey.builder()
            .withKeyData(new byte[16])
            .withRollingStartIntervalNumber(startIntervalNumber)
            .withTransmissionRiskLevel(2)
            .withSubmissionTimestamp(submissionTimestamp).build())
        .collect(Collectors.toList());
  }

}
