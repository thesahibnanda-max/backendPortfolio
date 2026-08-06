package net.sahibnanda.portfolio.abc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FakeFailingTest {

  @Test
  void failTest() {
    assertEquals(1, 2, "1 != 2");
  }


}
