from selenium.webdriver import Chrome, ChromeOptions
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys

from urllib.request import urlretrieve
from urllib.parse import urlparse
import os
import re
import time
import sys

'''
Scraper that uses Selenium to scrape specifically sahibinden.com for stock car photos.
'''


class SeleniumScraper:

    def __init__(self, driver_path, download_path, urls=["https://www.sahibinden.com/category/en/cars", "https://www.sahibinden.com/category/en/off-road-suv-pickup-trucks"], models={}):
        self.driver_path = driver_path
        self.driver = None
        self.download_path = download_path
        # self.test_path = f"{download_path}/test"
        #
        # self.test_set = test
        self.urls = urls
        # self.models = {
        #     # "Audi": ["A3", "Q7", "A4"],
        #     "Volvo": ["V50"],
        #     # "BMW": {"1 series": ["1.16", "3.20", "3.30", "3.25", "3.18"]},
        #     # "Volkswagen": ["Golf", "Polo", "Passat"]
        # }
        self.models = models
        self.total_downloads = {}
        for label in self.models:
            self.total_downloads[label] = {}
            for item in self.models[label]:
                self.total_downloads[label][item] = 0

        self.download_limit = 50

    def init_driver(self):
        if not os.path.exists(self.driver_path):
            raise FileNotFoundError(f"Provided driver path {self.driver_path} does not exist.")

        user_agent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Safari/537.36"

        # options = ChromeOptions()
        # options.add_argument(f"user-agent={user_agent}")
        # options.add_argument("start-maximized")
        # options.add_argument("disable-infobars")
        # options.add_argument('--profile-directory=Default')
        # options.add_argument('--incognito')
        # options.add_argument('--disable-plugins-discovery')
        # options.add_argument("--disable-extensions")

        self.driver = Chrome(self.driver_path)

    def scrape(self):
        self.init_driver()

        for url in self.urls:
            self.driver.get(url)
            # time.sleep(5)
            car_make_list = self.driver.find_elements_by_xpath("//div[contains(@data-value, 'Cars')]//ul[@class='categoryList jspScrollable']//li/a")
            if not car_make_list:
                car_make_list = self.driver.find_elements_by_xpath("//div[contains(@data-value, 'Off-Road, SUV & Pickup Trucks')]//ul[@class='categoryList jspScrollable']//li/a")

            brand_text_links = {}
            for web_element in car_make_list:
                model = web_element.get_attribute("title")
                if model in self.models:
                    brand_text_links[model] = web_element.get_attribute("href")

            for brand, link in brand_text_links.items():
                self.driver.get(link)
                # time.sleep(5)
                model_list = self.driver.find_elements_by_xpath("//div[@id='searchCategoryContainer']/div/div/ul/li/a")
                links_to_models = [model.get_attribute("href") for model in model_list]
                model_infos = [model.get_attribute("title") for model in model_list]
                for link_to_model, model_info in zip(links_to_models, model_infos):
                    check = self.check_brand_and_model(brand, model_info)
                    if check[0]:
                        model = check[1]
                        while self.total_downloads[brand][model] < self.download_limit:
                            self.driver.get(link_to_model)
                            time.sleep(5)
                            nav_buttons = self.driver.find_elements_by_xpath("//ul[@class='pageNaviButtons']/li/following-sibling::li[2]/a")
                            pages = [nav_button.get_attribute("href") for nav_button in nav_buttons]
                            for page in pages:
                                self.driver.get(page)
                                time.sleep(5)
                                links_to_ads = self.driver.find_elements_by_xpath("//*[@id='searchResultsTable']/tbody/tr/td[1]/a")
                                year_info = [int(item.text) for item in self.driver.find_elements_by_xpath("//*[@id='searchResultsTable']/tbody/tr/td[4]")]
                                links = [link.get_attribute("href") for link in links_to_ads]
                                for addr in links:
                                    if year_info[links.index(addr)] > 2007:
                                        self.driver.get(addr)
                                        time.sleep(5)
                                        images_list = self.driver.find_elements_by_xpath("//div[@class='classifiedDetailMainPhoto']/label[contains(@id, 'label_images')]/img")
                                        images_links = [image_link.get_attribute("data-src") for image_link in images_list]
                                        for src in images_links:
                                            time.sleep(5)
                                            label = f"{str.lower(brand)} {str.lower(model)}"
                                            download_path = os.path.join(self.download_path, label)
                                            if not os.path.exists(download_path):
                                                os.makedirs(download_path)
                                            parsed_url = urlparse(src).path
                                            filename = os.path.basename(parsed_url)
                                            download_path = f"{download_path}/{filename}"
                                            if os.path.exists(download_path):
                                                print(f"Exists: {download_path}")
                                                continue
                                            try:
                                                urlretrieve(src, download_path)
                                                self.total_downloads[brand][model] += 1
                                                print(self.total_downloads)
                                            except:
                                                continue
                                        if self.total_downloads[brand][model] >= self.download_limit:
                                            break
                                    if self.total_downloads[brand][model] >= self.download_limit:
                                        break
                                if self.total_downloads[brand][model] >= self.download_limit:
                                    break

    def check_brand_and_model(self, brand, model_info_from_page):
        print(f"Brand: {brand},\tModel: {self.models[brand]},\tInfo from page: {model_info_from_page}")
        for model in self.models[brand]:
            print(f"Checking for model: {model}")
            expr_to_match = re.compile(rf"{model}[a-zA-Z0-9]?")
            if expr_to_match.search(model_info_from_page):
                print(f"Values returned: {True} and {model}")
                return True, model
        print(f"Values returned: {False}")

        return False, None


if __name__ == '__main__':
    models = {
        "Renault": ["Megane", "Captur"],
        # "BMW": ["1 Series"],
        # "Opel": ["Astra"],
        # "Nissan": ["Micra"],
        # "Volkswagen": ["Passat", "Golf", "Polo"],
        # "Audi": ["A3", "A4", "Q7"]
    }
    SeleniumScraper("/home/alp/Desktop/chromedriver", download_path="/media/alp/Yeni Birim/car_dataset_test/", models=models).scrape()
    # SeleniumScraper("/home/alp/Desktop/chromedriver", download_path="./car_dataset").scrape()
