def call(String appName) {
    // Load app configuration
    def appConfig = libraryResource("apps/${appName}.groovy")
    def config = evaluate(appConfig)
    def podTemplate = languageHandlers.getPodTemplate(config.language)
    
    echo "Running pipeline for app: ${appName}"
    echo "Language: ${config.language}, Pod: ${podTemplate}"
    
    node(podTemplate) {
        stage('Checkout') {
            git branch: config.gitBranch, url: config.gitUrl
        }
        
        if (config.stages.build) {
            stage('Build') {
                container(podTemplate) {
                    languageHandlers.buildProject(config.language, config.buildTool)
                }
            }
        }
        
        if (config.stages.test) {
            stage('Test') {
                container(podTemplate) {
                    languageHandlers.testProject(config.language, config.buildTool)
                }
            }
        }
        
        if (config.stages.package) {
            stage('Package') {
                container(podTemplate) {
                    languageHandlers.packageProject(config.language, config.buildTool)
                }
            }
        }
        
        if (config.stages.deploy?.enabled) {
            stage('Deploy') {
                container(podTemplate) {
                    echo "Deploying to: ${config.stages.deploy.target}"
                    sh "kubectl apply -f ${config.stages.deploy.manifest}"
                }
            }
        }
        
        stage('Cleanup') {
            cleanWs()
        }
    }
}