# ReportPortal agent for Cucumber v.6 and v.7

The latest version is: 5.3.0

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