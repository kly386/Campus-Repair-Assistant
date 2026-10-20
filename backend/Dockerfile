# 后端 Dockerfile
# 构建阶段：使用 Maven 编译打包
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 运行阶段：仅保留 JRE 运行
# 后端以可执行 WAR 打包（pom.xml 中 finalName=ROOT），
# 同一个 WAR 既支持 java -jar 独立运行，也支持部署到外部 Tomcat 11。
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/ROOT.war app.war
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.war"]