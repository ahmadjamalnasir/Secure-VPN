# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Copy manifests first so the dependency layer caches across source changes.
COPY package.json package-lock.json ./

# `npm ci` installs exactly what the lockfile pins, unlike `npm install` which
# can silently resolve different transitive versions on every build.
RUN npm ci

COPY . .

# Typechecks as part of `build`, so a type error fails the image.
RUN npm run build

# Serve stage
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/build /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
