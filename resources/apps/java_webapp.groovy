return [
    podTemplate: 'maven',
    gitUrl: 'https://github.com/example/java-webapp.git',
    gitBranch: 'main',
    stages: [
        build: [
            enabled: true,
            commands: [
                'mvn clean compile -B'
            ]
        ],
        test: [
            enabled: true,
            commands: [
                'mvn test -B'
            ]
        ],
        package: [
            enabled: true,
            commands: [
                'mvn package -DskipTests -B'
            ]
        ],
        deploy: [
            enabled: true,
            target: 'production',
            commands: [
                'kubectl apply -f k8s/webapp.yaml'
            ]
        ]
    ]
]