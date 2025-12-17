FROM python:3.11-slim
WORKDIR /app

# Copy only Python files and .txt files as requested
COPY *.py *.txt ./

# Install requirements if present
RUN if [ -f requirements.txt ]; then pip install --no-cache-dir -r requirements.txt; fi

EXPOSE 8020
CMD ["python", "databaseHandlerServer.py"]
