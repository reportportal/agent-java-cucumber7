@smoke @test
Feature: Test rule keyword

  @first @key:value
  Rule: The first rule
    Scenario: The first scenario
      Given I have empty step
      Then I have another empty step

    Scenario: The second scenario
      Given I have empty step
      Then I have one more empty step

  @second @key:value
  Rule: The second rule
    Scenario: The third scenario
      Given I have empty step
      Then I have one more else empty step
