Feature: Basic test with parameters

  @test
  Scenario Outline: Test with different parameters
    Given It is test with parameters
    When I have parameter <str>
    Then I emit number <parameters> on level info

    @tc_id:JIRA-4321
    Examples:
      | str      | parameters |
      | "first"  | 123        |
      | "second" | 12345      |
      | "third"  | 12345678   |
