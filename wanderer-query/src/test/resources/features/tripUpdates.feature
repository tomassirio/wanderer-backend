Feature: Trip Updates Query

  Background:
    Given an empty system

  Scenario: Get all trip updates for a trip
    Given a user exists with username "alice" and email "alice@example.com"
    And I have a valid token for that user with roles "USER"
    And a trip exists with name "Test Trip"
    And a trip update exists with message "First location update"
    And a trip update exists with message "Second location update"
    And a trip update exists with message "Third location update"
    When I get all trip updates for that trip
    Then the response status should be 200
    And the response contains at least one trip update id
    And the response should contain 3 trip updates

  Scenario: Get all trip updates for a trip without token results in 401
    Given a user exists with username "bob" and email "bob@example.com"
    And a trip exists with name "Test Trip"
    And a trip update exists with message "Location update"
    When I get all trip updates for that trip without token
    Then the response status should be 401

  Scenario: Get a specific trip update by id
    Given a user exists with username "alice" and email "alice@example.com"
    And I have a valid token for that user with roles "USER"
    And a trip exists with name "Test Trip"
    And a trip update exists with message "Specific update" and battery 85
    When I get that trip update by id
    Then the response status should be 200
    And the response contains a trip update id
    And the trip update message should be "Specific update"
    And the trip update battery should be 85

  Scenario: Get a specific trip update without token results in 401
    Given a user exists with username "bob" and email "bob@example.com"
    And a trip exists with name "Test Trip"
    And a trip update exists with message "Hidden update"
    When I get that trip update by id without token
    Then the response status should be 401

  Scenario: Get trip updates with location data
    Given a user exists with username "alice" and email "alice@example.com"
    And I have a valid token for that user with roles "USER"
    And a trip exists with name "Test Trip"
    And a trip update exists with message "Boston" and location 42.3601, -71.0589
    And a trip update exists with message "New York" and location 40.7128, -74.0060
    When I get all trip updates for that trip
    Then the response status should be 200
    And the response should contain 2 trip updates
    And the trip updates should have location data

  Scenario: Get trip updates for non-existent trip returns 404
    Given a user exists with username "alice" and email "alice@example.com"
    And I have a valid token for that user with roles "USER"
    When I get all trip updates for non-existent trip
    Then the response status should be 404

  Scenario: Get trip updates with various battery levels
    Given a user exists with username "alice" and email "alice@example.com"
    And I have a valid token for that user with roles "USER"
    And a trip exists with name "Test Trip"
    And a trip update exists with message "Full battery" and battery 100
    And a trip update exists with message "Half battery" and battery 50
    And a trip update exists with message "Low battery" and battery 10
    When I get all trip updates for that trip
    Then the response status should be 200
    And the response should contain 3 trip updates
    And the trip updates should have different battery levels

  Scenario: Get trip update that does not exist returns 404
    Given a user exists with username "alice" and email "alice@example.com"
    And I have a valid token for that user with roles "USER"
    When I get a non-existent trip update by id
    Then the response status should be 404

