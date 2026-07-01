# TODO — Code Quality Issues

## Gradle Deprecation Warning (Upstream)
- Build emits: *"Using a Project object as a dependency notation has been deprecated"*
- Traced to `com.android.build.gradle.internal.dependency.VariantDependenciesBuilder.build()` in **AGP 9.2.1**
- Not fixable in project code — requires a newer AGP release from Google
- Will become an error in Gradle 10
