# ---- Build stage: compile the React app ----
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies first so they are cached until the lockfile changes
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Serve stage: nginx serves the static files and forwards /api to the backend ----
FROM nginx:stable-alpine

# Where nginx forwards /api requests (the backend service name in docker-compose)
ENV BACKEND_URL=http://backend:8080

# The nginx image fills ${BACKEND_URL} into files under templates/ when it starts
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
