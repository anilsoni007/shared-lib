def call(String appName) {
    // Load app configuration
    def appConfig = libraryResource("apps/${appName}.groovy")
    def config = evaluate(appConfig)
    
    echo "Running pipeline for app: ${appName}"
    echo "Using pod template: ${config.podTemplate}"
    
    node(config.podTemplate) {
        stage('Checkout') {
            git branch: config.gitBranch, url: config.gitUrl
        }
        
        if (config.stages.build.enabled) {
            stage('Build') {
                container(config.podTemplate) {
                    config.stages.build.commands.each { cmd ->
                        sh cmd
                    }
                }
            }
        }
        
        if (config.stages.test.enabled) {
            stage('Test') {
                container(config.podTemplate) {
                    config.stages.test.commands.each { cmd ->
                        sh cmd
                    }
                }
            }
        }
        
        if (config.stages.package?.enabled) {
            stage('Package') {
                container(config.podTemplate) {
                    config.stages.package.commands.each { cmd ->
                        sh cmd
                    }
                }
            }
        }
        
        if (config.stages.deploy.enabled) {
            stage('Deploy') {
                container(config.podTemplate) {
                    echo "Deploying to: ${config.stages.deploy.target}"
                    config.stages.deploy.commands.each { cmd ->
                        sh cmd
                    }
                }
            }
        }
        
        stage('Cleanup') {
            cleanWs()
        }
    }
}