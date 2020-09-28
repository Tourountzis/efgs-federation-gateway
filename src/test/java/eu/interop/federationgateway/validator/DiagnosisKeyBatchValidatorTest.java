package eu.interop.federationgateway.validator;

import com.google.protobuf.ByteString;
import eu.interop.federationgateway.TestData;
import eu.interop.federationgateway.batchsigning.SignatureGenerator;
import eu.interop.federationgateway.config.EfgsProperties;
import eu.interop.federationgateway.filter.CertificateAuthentificationFilter;
import eu.interop.federationgateway.model.EfgsProto;
import eu.interop.federationgateway.repository.CertificateRepository;
import eu.interop.federationgateway.repository.DiagnosisKeyEntityRepository;
import eu.interop.federationgateway.testconfig.EfgsTestKeyStore;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = EfgsTestKeyStore.class)
public class DiagnosisKeyBatchValidatorTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private EfgsProperties properties;

  @Autowired
  private DiagnosisKeyEntityRepository diagnosisKeyEntityRepository;

  @Autowired
  private CertificateAuthentificationFilter certFilter;

  @Autowired
  private CertificateRepository certificateRepository;

  private SignatureGenerator signatureGenerator;

  private MockMvc mockMvc;

  @Before
  public void setup() throws NoSuchAlgorithmException, CertificateException, IOException,
    OperatorCreationException, InvalidKeyException, SignatureException, KeyStoreException {
    TestData.insertCertificatesForAuthentication(certificateRepository);

    diagnosisKeyEntityRepository.deleteAll();
    mockMvc = MockMvcBuilders
      .webAppContextSetup(context)
      .addFilter(certFilter)
      .build();
  }

  @Test
  public void testInvalidTransmissionRiskLevel() throws Exception {
    int[] invalidTRisk = {-1, 9, 100};

    for (int transmissionRisk : invalidTRisk) {
      EfgsProto.DiagnosisKey diagnosisKey =
        TestData.getDiagnosisKeyProto().toBuilder()
            .setKeyData(ByteString.copyFromUtf8("abcd1234abcd1234")) // Without valid key data, that validator trips first.
            .setTransmissionRiskLevel(transmissionRisk)
            .setRollingPeriod(144)
            .build();

      EfgsProto.DiagnosisKeyBatch batch = EfgsProto.DiagnosisKeyBatch.newBuilder()
        .addAllKeys(Arrays.asList(diagnosisKey)).build();

      mockMvc.perform(post("/diagnosiskeys/upload")
        .contentType("application/protobuf; version=1.0")
        .header("batchTag", TestData.FIRST_BATCHTAG)
        .header("batchSignature", "signature")
        .header(properties.getCertAuth().getHeaderFields().getThumbprint(), TestData.AUTH_CERT_HASH)
        .header(properties.getCertAuth().getHeaderFields().getDistinguishedName(), TestData.DN_STRING_DE)
        .content(batch.toByteArray())
      )
        .andExpect(status().isBadRequest());
    }
  }

  @Test
  public void testInvalidKeyDataEmpty() throws Exception {
    EfgsProto.DiagnosisKey diagnosisKey =
      TestData.getDiagnosisKeyProto().toBuilder()
        .setKeyData(ByteString.EMPTY)
        .build();

    EfgsProto.DiagnosisKeyBatch batch = EfgsProto.DiagnosisKeyBatch.newBuilder()
      .addAllKeys(Arrays.asList(diagnosisKey)).build();

    mockMvc.perform(post("/diagnosiskeys/upload")
      .contentType("application/protobuf; version=1.0")
      .header("batchTag", TestData.FIRST_BATCHTAG)
      .header("batchSignature", "signature")
      .header(properties.getCertAuth().getHeaderFields().getThumbprint(), TestData.AUTH_CERT_HASH)
      .header(properties.getCertAuth().getHeaderFields().getDistinguishedName(), TestData.DN_STRING_DE)
      .content(batch.toByteArray())
    )
      .andExpect(status().isBadRequest());
  }

  @Test
  public void testInvalidKeyData() throws Exception {
    EfgsProto.DiagnosisKey diagnosisKey =
      TestData.getDiagnosisKeyProto().toBuilder()
        .setKeyData(ByteString.copyFromUtf8("1234")) // Invalid key length.
        .build();

    EfgsProto.DiagnosisKeyBatch batch = EfgsProto.DiagnosisKeyBatch.newBuilder()
      .addAllKeys(Arrays.asList(diagnosisKey)).build();

    mockMvc.perform(post("/diagnosiskeys/upload")
      .contentType("application/protobuf; version=1.0")
      .header("batchTag", TestData.FIRST_BATCHTAG)
      .header("batchSignature", "signature")
      .header(properties.getCertAuth().getHeaderFields().getThumbprint(), TestData.AUTH_CERT_HASH)
      .header(properties.getCertAuth().getHeaderFields().getDistinguishedName(), TestData.DN_STRING_DE)
      .content(batch.toByteArray())
    )
      .andExpect(status().isBadRequest());
  }

  @Test
  public void testInvalidRollingStartIntervalNumber() throws Exception {
    EfgsProto.DiagnosisKey diagnosisKey =
      TestData.getDiagnosisKeyProto().toBuilder().setRollingStartIntervalNumber(0).build();

    EfgsProto.DiagnosisKeyBatch batch = EfgsProto.DiagnosisKeyBatch.newBuilder()
      .addAllKeys(Arrays.asList(diagnosisKey)).build();

    mockMvc.perform(post("/diagnosiskeys/upload")
      .contentType("application/protobuf; version=1.0")
      .header("batchTag", TestData.FIRST_BATCHTAG)
      .header("batchSignature", "signature")
      .header(properties.getCertAuth().getHeaderFields().getThumbprint(), TestData.AUTH_CERT_HASH)
      .header(properties.getCertAuth().getHeaderFields().getDistinguishedName(), TestData.DN_STRING_DE)
      .content(batch.toByteArray())
    )
      .andExpect(status().isBadRequest());
  }

  @Test
  public void testInvalidRollingPeriod() throws Exception {
    EfgsProto.DiagnosisKey diagnosisKey =
      TestData.getDiagnosisKeyProto().toBuilder()
        .setKeyData(ByteString.copyFromUtf8("abcd1234abcd1234"))
        .setRollingPeriod(145)
        .setTransmissionRiskLevel(6)
        .build();

    EfgsProto.DiagnosisKeyBatch batch = EfgsProto.DiagnosisKeyBatch.newBuilder()
      .addAllKeys(Arrays.asList(diagnosisKey)).build();

    mockMvc.perform(post("/diagnosiskeys/upload")
      .contentType("application/protobuf; version=1.0")
      .header("batchTag", TestData.FIRST_BATCHTAG)
      .header("batchSignature", "signature")
      .header(properties.getCertAuth().getHeaderFields().getThumbprint(), TestData.AUTH_CERT_HASH)
      .header(properties.getCertAuth().getHeaderFields().getDistinguishedName(), TestData.DN_STRING_DE)
      .content(batch.toByteArray())
    )
      .andExpect(status().isBadRequest());
  }

}
