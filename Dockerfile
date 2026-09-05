FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
ARG SERVICE_PROJECT
WORKDIR /src
ENV NUGET_PACKAGES=/tmp/nuget/packages
ENV NUGET_HTTP_CACHE_PATH=/tmp/nuget/http-cache
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
