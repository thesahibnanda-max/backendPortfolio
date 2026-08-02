package net.sahibnanda.portfolio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.sahibnanda.portfolio.config.GitHubProperties;
import net.sahibnanda.portfolio.models.GitHubCommit;
import net.sahibnanda.portfolio.models.GitHubRepository;
import net.sahibnanda.portfolio.models.GitHubUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GitHubClientTest {

  private static GitHubClient gitHubClient;

  @BeforeAll
  static void setup() {
    gitHubClient = new GitHubClient(new GitHubProperties("https://api.github.com"));
  }

  @Test
  void getUserDetails() {
    GitHubUser user = gitHubClient.getUserDetails("octocat");
    System.out.println(user);

    assertNotNull(user);
    assertEquals(583231L, user.getId());
    assertEquals("octocat", user.getLogin());
    assertEquals("User", user.getType());
  }

  @Test
  void listUserRepositories() {
    List<GitHubRepository> repos = gitHubClient.listUserRepositories("octocat", 1);
    System.out.println(repos);

    assertNotNull(repos);
    assertFalse(repos.isEmpty());
    assertTrue(repos.stream().anyMatch(r -> "Hello-World".equals(r.getName())));
  }

  @Test
  void listCommitsByAuthor() {
    List<GitHubCommit> commits =
        gitHubClient.listCommitsByAuthor("octocat", "Hello-World", "octocat", 1);
    System.out.println(commits);

    assertNotNull(commits);
    assertFalse(commits.isEmpty());
  }

  @Test
  void getCommit() {
    GitHubCommit commit =
        gitHubClient.getCommit(
            "octocat", "Hello-World", "7fd1a60b01f91b314f59955a4e4d4e80d8edf11d");
    System.out.println(commit);

    assertNotNull(commit);
    assertEquals("7fd1a60b01f91b314f59955a4e4d4e80d8edf11d", commit.getSha());
    assertNotNull(commit.getCommit());
    assertNotNull(commit.getStats());
  }
}
