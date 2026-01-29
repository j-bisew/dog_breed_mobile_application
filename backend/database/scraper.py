import os
import requests
import time
import sqlite3
from bs4 import BeautifulSoup

def scrapeDogBreeds():
    # Connect to the database (assuming it's in the same directory or adjust path as needed)
    conn = sqlite3.connect('raceDB.db')
    cursor = conn.cursor()

    # For each breed in class_names.txt, fetch its correct name (e.g., "German Shepherd") and a short description from Wikipedia. Also, download the main image of the breed and save it locally in backend/database/racesFolder/{breedName}/ folder as mainPhoto.jpg and summary (no extension, with formatted content).

    # Assuming class_names.txt is in the same directory as scraper.py
    with open('class_names.txt', 'r') as f:
        breeds = [line.strip() for line in f if line.strip()]

    notSuccessful = []

    headers = {
        'User-Agent': 'DogBreedScraper/1.0 (your-email@example.com)'  # Replace with your actual email or app details
    }

    for breed in breeds:
        try:
            # Fetch Wikipedia page with user-agent
            url = f"https://en.wikipedia.org/wiki/{breed.replace(' ', '_')}"
            print(f"Processing breed: {breed} from URL: {url}")
            response = requests.get(url, headers=headers)
            soup = BeautifulSoup(response.content, 'html.parser')

            print(soup.prettify()[:500])  # Print first 500 characters of the page for debugging
            
            # Get correct name from title
            title = soup.find('h1', {'id': 'firstHeading'}).text.strip()
            
            # Get short description (first paragraph)
            # description = soup.find('p').text.strip()
            # print(soup.find('p').prettify())  # Print the first paragraph for debugging
            description = soup.find_all('p')[1].text.strip() if len(soup.find_all('p')) > 1 else description
            print("Description extracted:", description)
            
            # Find main image in infobox
            infobox = soup.find('table', {'class': 'infobox'})
            print("Infobox extracted:", infobox.prettify()[:500] if infobox else "No infobox found")
            if infobox:
                img_tag = infobox.find('img')
                print("Image tag extracted:", img_tag)
                if img_tag:
                    img_url = 'https:' + img_tag['src']
                    img_response = requests.get(img_url, headers=headers)
                    
                    # Create folder using the corrected title
                    folder_name = title.replace(' ', '_').lower()
                    folder_path = os.path.join('..', 'database', 'racesFolder', folder_name)
                    os.makedirs(folder_path, exist_ok=True)
                    
                    # Save image
                    img_path = os.path.join(folder_path, 'mainPhoto.jpg')
                    with open(img_path, 'wb') as f:
                        f.write(img_response.content)
                    
                    # Save summary with formatted content
                    summary_path = os.path.join(folder_path, 'summary')
                    with open(summary_path, 'w', encoding='utf-8') as f:
                        f.write(f"Full Name: {title}\nDescription: {description}")
                    
                    # Insert into database
                    cursor.execute('INSERT INTO dog_races (name, folderName) VALUES (?, ?)', (folder_name, folder_name))
                    conn.commit()
            
            # Respect robot policy by adding a delay between requests
            time.sleep(1)
            print("\n\n")
        except Exception as e:
            print(f"Failed to process breed: {breed}. Error: {e}")
            notSuccessful.append(breed)
            # Create a folder and add placeholder files
            folder_name = breed.replace(' ', '_').lower()
            folder_path = os.path.join('..', 'database', 'racesFolder', folder_name)
            os.makedirs(folder_path, exist_ok=True)
            placeholder_img_path = os.path.join(folder_path, 'mainPhoto.jpg')
            with open(placeholder_img_path, 'wb') as f:
                f.write(b'')  # Empty placeholder
            placeholder_summary_path = os.path.join(folder_path, 'summary')
            with open(placeholder_summary_path, 'w', encoding='utf-8') as f:
                f.write(f"Full Name: {breed}\nDescription: Unfortunately, we could not retrieve information for this breed at this time.")
            cursor.execute('INSERT INTO dog_races (name, folderName) VALUES (?, ?)', (folder_name, folder_name))
            conn.commit()

    # Close the database connection
    conn.close()



    print("Breeds that were not processed successfully:")
    for breed in notSuccessful:
        print(breed)

def addMissingBreedsToDB():
    conn = sqlite3.connect('raceDB.db')
    cursor = conn.cursor()

    # For each breed in class_names.txt, fetch its correct name (e.g., "German Shepherd") and a short description from Wikipedia. Also, download the main image of the breed and save it locally in backend/database/racesFolder/{breedName}/ folder as mainPhoto.jpg and summary (no extension, with formatted content).

    # Assuming class_names.txt is in the same directory as scraper.py
    with open('class_names.txt', 'r') as f:
        breeds = [line.strip() for line in f if line.strip()]
    
    for breed in breeds:
        folderName = breed.replace(' ', '_').lower()
        cursor.execute('SELECT COUNT(*) FROM dog_races WHERE folderName = ?', (folderName,))
        count = cursor.fetchone()[0]
        if count == 0:
            folder_name = breed.replace(' ', '_').lower()
            folder_path = os.path.join('..', 'database', 'racesFolder', folder_name)
            os.makedirs(folder_path, exist_ok=True)
            placeholder_img_path = os.path.join(folder_path, 'mainPhoto.jpg')
            with open(placeholder_img_path, 'wb') as f:
                f.write(b'')  # Empty placeholder
            placeholder_summary_path = os.path.join(folder_path, 'summary')
            with open(placeholder_summary_path, 'w', encoding='utf-8') as f:
                f.write(f"Full Name: {breed}\nDescription: Unfortunately, we could not retrieve information for this breed at this time.")
            cursor.execute('INSERT INTO dog_races (name, folderName) VALUES (?, ?)', (folder_name, folder_name))
            conn.commit()
    conn.close()


def addSamePhotoToEachFolder(photoPath):
    # For testing purposes, copy the same photo into each breed folder as mainPhoto.jpg
    with open(photoPath, 'rb') as f:
        photoData = f.read()
    racesFolderPath = os.path.join('..', 'database', 'racesFolder')
    for folderName in os.listdir(racesFolderPath):
        folderPath = os.path.join(racesFolderPath, folderName)
        if os.path.isdir(folderPath):
            mainPhotoPath = os.path.join(folderPath, 'mainPhoto.jpg')
            with open(mainPhotoPath, 'wb') as f:
                f.write(photoData)
            print(f"Copied photo to {mainPhotoPath}")
    
if __name__ == "__main__":
    #scrapeDogBreeds()
    
    addMissingBreedsToDB()
    addSamePhotoToEachFolder('mainPhoto.jpg')