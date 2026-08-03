package net.sahibnanda.portfolio.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroqModelTest {

  @Test
  void llama33HasNoReasoningEffort() {
    assertThat(GroqModel.LLAMA_3_3_70B.getModelId())
        .isEqualTo("llama-3.3-70b-versatile");
    assertThat(GroqModel.LLAMA_3_3_70B.getReasoningEffort()).isEmpty();
  }

  @Test
  void gptOss20bHasMediumReasoningEffort() {
    assertThat(GroqModel.GPT_OSS_20B.getModelId())
        .isEqualTo("openai/gpt-oss-20b");
    assertThat(GroqModel.GPT_OSS_20B.getReasoningEffort())
        .contains(ReasoningEffort.MEDIUM);
  }

  @Test
  void qwen36HasNoneReasoningEffort() {
    assertThat(GroqModel.QWEN_3_6_27B.getModelId())
        .isEqualTo("qwen/qwen3.6-27b");
    assertThat(GroqModel.QWEN_3_6_27B.getReasoningEffort())
        .contains(ReasoningEffort.NONE);
  }

  @Test
  void gptOss120bHasMediumReasoningEffort() {
    assertThat(GroqModel.GPT_OSS_120B.getModelId())
        .isEqualTo("openai/gpt-oss-120b");
    assertThat(GroqModel.GPT_OSS_120B.getReasoningEffort())
        .contains(ReasoningEffort.MEDIUM);
  }
}
