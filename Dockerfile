FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
ARG SERVICE_PROJECT
WORKDIR /src
COPY . .
RUN dotnet restore "${SERVICE_PROJECT}/${SERVICE_PROJECT}.csproj"
RUN dotnet publish "${SERVICE_PROJECT}/${SERVICE_PROJECT}.csproj" -c Release -o /app/publish --no-restore /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS final
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/publish .
EXPOSE 8080
ENTRYPOINT ["dotnet"]
