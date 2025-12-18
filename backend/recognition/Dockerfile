FROM python:3.11-slim
WORKDIR /app

# Copy only Python files and .txt files as requested
COPY *.py *.txt ./

# Use libgl1 instead of libgl1-mesa-glx
RUN apt-get update && apt-get install -y \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Install requirements if present; upgrade pip first and use `python -m pip`
# 1. Install pip tools
RUN python -m pip install --upgrade pip setuptools wheel

# 2. Force-install CPU Torch first 
# Using --index-url ensures we ONLY look at the CPU-only repo
RUN python -m pip install --no-cache-dir \
    torch==2.1.0 \
    torchvision==0.16.0 \
    torchaudio==2.1.0 \
    --index-url https://download.pytorch.org/whl/cpu

# 3. Install other requirements from your file
COPY requirements.txt .
RUN python -m pip install --no-cache-dir -r requirements.txt

# 4. Install Ultralytics WITHOUT --no-deps
# Since Torch is already installed, it will skip torch and just get the helpers
RUN python -m pip install --no-cache-dir ultralytics==8.3.23

RUN python -m pip install --upgrade tensorflow-cpu

EXPOSE 8030
CMD ["python", "recognitionServer.py"]