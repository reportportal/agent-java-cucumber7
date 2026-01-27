Feature: TestNG retry detection

  Scenario: Retry is marked on second run
    Given I fail 1 times
    Then I pass
