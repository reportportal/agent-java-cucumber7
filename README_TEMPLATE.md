# ReportPortal agent for Cucumber v.6 and v.7
Cucumber JVM version [6.0.0; ) adapter

> **DISCLAIMER**: We use Google Analytics for sending anonymous usage information such as agent's and client's names,
> and their versions after a successful launch start. This information might help us to improve both ReportPortal
> backend and client sides. It is used by the ReportPortal team only and is not supposed for sharing with 3rd parties.

[![Maven Central](https://img.shields.io/maven-central/v/com.epam.reportportal/agent-java-cucumber7.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.epam.reportportal/agent-java-cucumber7)
[![CI Build](https://github.com/reportportal/agent-java-cucumber7/actions/workflows/ci.yml/badge.svg)](https://github.com/reportportal/agent-java-cucumber7/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/reportportal/agent-java-cucumber7/graph/badge.svg?token=NS72PTFF9C)](https://codecov.io/gh/reportportal/agent-java-cucumber7)
[![Join Slack chat!](https://img.shields.io/badge/slack-join-brightgreen.svg)](https://slack.epmrpp.reportportal.io/)
[![stackoverflow](https://img.shields.io/badge/reportportal-stackoverflow-orange.svg?style=flat)](http://stackoverflow.com/questions/tagged/reportportal)
[![Build with Love](https://img.shields.io/badge/build%20with-❤%EF%B8%8F%E2%80%8D-lightgrey.svg)](http://reportportal.io?style=flat)

The latest version is: $LATEST_VERSION

## Breaking changes

This Agent generates code references in a different way than the previous Agents, and, as a result, it also generates
different Test Case IDs. This means that the history of your tests will be broken, and you will not be able to see the
results reported with the previous Agent on the History views of ReportPortal UI. These changes cannot be migrated, so
you will need to accept this as a breaking change. All newly reported tests will be tracked as usual.

## Difference between this and previous versions

The new Cucumber agent for ReportPortal introduces several significant improvements over the previous versions.

### Structural Changes

- **Removed "Root User Story"**: The artificial "dummy" root suite that was previously created has been eliminated,
  making the test structure cleaner and more intuitive. This suite was introduced for ReportPortal server versions 4.x 
  compatibility and prevented to maintain backward compatibility. Now it is time to get rid of it.

- **Improved Hierarchy**: Before/After hooks are now properly nested within their parent Scenarios and Steps, creating a
  more logical test structure.

- **Removed StepReporter Class**: The `StepReporter` was already marked as deprecated in the previous versions due to
  the fact that it went against the BDD entity model, which stated a `Scenario` (synonym to `Example`) is the concrete
  example that illustrates a business rule, a specification, documentation and is also a test. Thus, the `StepReporter`
  which treated steps as independent tests violated these principles and was subject to removal.

- **Display name for Features without names**: The Agent now uses a Feature relative path as suite name when a Feature
  does not have a name.

### Test Identification Improvements

- **Enhanced Code References**: Implemented consistent code reference generation similar with what we have in other BDD
  agents. Code references are now generated based on the file path and Scenario name, rather than the file path and
  line number. This change helps to avoid issues when changes to the Feature file breaks history tracking for Scenarios,
  which line numbers were shifted.

- **Test Case ID Support**: Added comprehensive Test Case ID handling, including support for the special `@tc_id` tag.

### Tag Processing Enhancements

- **Tag Format Cleanup**: Removed `@` prefix from tags for cleaner attribute representation.

- **Key-Value Attributes**: Added support for key-value attributes separated by colon (`:`), allowing more structured
  metadata.

- **Rule Keyword Support**: Tags are now properly supported for the Cucumber Rule keyword.

These changes make the ReportPortal Cucumber agent more powerful, easier to use, and better aligned with modern testing
practices.

