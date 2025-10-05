# Jenkins Shared Library for EKS Pod Auto-Assignment

This shared library automatically detects project types and assigns appropriate Kubernetes pod templates for building and executing code in Jenkins running on EKS.

## Features

- **Auto Project Detection**: Detects Maven, Gradle, Node.js, Python, Docker, and Go projects
- **Smart Pod Assignment**: Automatically selects the right pod template based on project type
- **Predefined Stages**: Build, Test, Package, and Deploy stages with project-specific logic
- **Flexible Configuration**: Customizable pipeline behavior through configuration parameters

## Usage

### Basic Usage

```groovy
// Jenkinsfile
@Library('your-shared-lib') _

autoDetectBuild()
```

### Advanced Configuration

```groovy
// Jenkinsfile
@Library('your-shared-lib') _

autoDetectBuild([
    runTests: true,
    package: true,
    deploy: false,
    deployCommand: 'kubectl apply -f k8s/',
    notifications: true
])
```

### Alternative Functions

```groovy
// Using buildProject function
buildProject([
    runTests: true,
    package: true
])

// Using smartPipeline (declarative pipeline style)
smartPipeline([
    deploy: true,
    deployTarget: 'staging'
])
```

## Project Detection Rules

| Project Type | Detection Files |
|--------------|----------------|
| Maven | `pom.xml` |
| Gradle | `build.gradle`, `build.gradle.kts` |
| Node.js | `package.json` |
| Python | `requirements.txt`, `setup.py`, `pyproject.toml` |
| Docker | `Dockerfile` |
| Go | `go.mod`, `main.go` |
| Java (default) | fallback option |

## Pod Template Mapping

| Project Type | Pod Template |
|--------------|-------------|
| Maven | `maven` |
| Gradle | `gradle` |
| Node.js | `nodejs` |
| Python | `python` |
| Docker | `docker` |
| Go | `java` |
| Java | `java` |

## Configuration Options

- `runTests` (boolean): Run test stage (default: true)
- `package` (boolean): Run package stage (default: true)
- `deploy` (boolean): Run deploy stage (default: false)
- `deployCommand` (string): Custom deployment command
- `deployScript` (string): Path to deployment script
- `notifications` (boolean): Enable build notifications

## Setup Instructions

1. **Configure Shared Library in Jenkins**:
   - Go to Manage Jenkins → Configure System
   - Add Global Pipeline Libraries
   - Name: `your-shared-lib`
   - Default version: `main`
   - Retrieval method: Modern SCM
   - Source Code Management: Git
   - Repository URL: `<your-git-repo-url>`

2. **Use in Jenkinsfile**:
   ```groovy
   @Library('your-shared-lib') _
   autoDetectBuild()
   ```

## Examples

### Maven Project
```groovy
@Library('shared-lib') _
autoDetectBuild([
    runTests: true,
    package: true
])
```

### Node.js with Deployment
```groovy
@Library('shared-lib') _
autoDetectBuild([
    deploy: true,
    deployCommand: 'npm run deploy'
])
```

### Docker Build
```groovy
@Library('shared-lib') _
autoDetectBuild([
    package: true,
    deploy: true,
    deployCommand: 'docker push myregistry/myapp:${BUILD_NUMBER}'
])
```

## Supported Pod Templates

Ensure your Jenkins configuration includes these pod templates:
- `maven` - Maven builds
- `gradle` - Gradle builds  
- `nodejs` - Node.js applications
- `python` - Python applications
- `docker` - Docker builds
- `java` - Generic Java builds