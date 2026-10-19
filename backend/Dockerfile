# API do VYBE para hospedar (Render, Railway, Fly...). Compila dentro do Docker, então
# o servidor não precisa de Java nem Maven instalados.

# Etapa 1: compila o .jar
FROM maven:3.9-eclipse-temurin-21 AS compilacao
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Etapa 2: só o Java para rodar (imagem bem menor)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilacao /app/target/vybe-api-*.jar app.jar
# Feito para os 512 MB do plano grátis: usa no máximo 70% da memória, coleta de lixo simples
# e compilação rápida (sobe mais depressa depois que o servidor "acorda").
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m"
ENV SPRING_PROFILES_ACTIVE=nuvem
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
