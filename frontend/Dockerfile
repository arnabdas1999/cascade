# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Runtime stage: serve with nginx ----
FROM nginx:alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html

# Forward /api/* to the backend service
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
