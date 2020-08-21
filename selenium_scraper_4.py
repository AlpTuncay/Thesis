from selenium.webdriver import Chrome, ChromeOptions
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import Select
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys

from urllib.request import urlretrieve
from urllib.parse import urlparse
import os
import re
import time
import sys

from selenium_scraper_3 import SeleniumScraper

class SeleniumScraper(SeleniumScraper):

    def __init__(self, driver_path, download_path, urls, models={"Volvo": ["V40"],"Volkswagen": ["Polo"]}):
        super().__init__(driver_path, download_path, urls, models)

        self.models = models

        self.download_limit = 100

        if not os.path.exists(self.download_path):
            os.makedirs(self.download_path)

    def scrape(self):
        self.init_driver()
            # WebDriverWait(self.driver, 10).until(EC.frame_to_be_available_and_switch_to_it((By.XPATH,'//iframe[@title="TrustArc Cookie Consent Manager"]')))
            # WebDriverWait(self.driver, 10).until(EC.element_to_be_clickable((By.XPATH,"//a[@class='call'][text()='Agree and Proceed']"))).click()
        for brand, models in self.models.items():
            for model in models:
                for url in self.urls:
                    self.driver.get(url)
                    time.sleep(10)

                    brand_drop_down = Select(self.driver.find_element_by_xpath("//select[@name='brand_id']"))
                    brand_drop_down.select_by_visible_text(brand)

                    WebDriverWait(self.driver, 5).until(EC.element_to_be_clickable((By.XPATH, "//select[@name='model_id']")))

                    model_drop_down  = Select(self.driver.find_element_by_xpath("//select[@name='model_id']"))
                    model_drop_down.select_by_visible_text(model)

                    search_btn = self.driver.find_element_by_xpath("//button[@type='submit']")
                    search_btn.click()

                    WebDriverWait(self.driver, 5).until(EC.presence_of_element_located((By.XPATH, "//*[@id='main-body']/div[4]/div/div[3]/div/div[4]/div[2]")))

                    number_of_pages = int(self.driver.find_element_by_xpath("//a[contains(text(),'Next ')]/preceding-sibling::span[1]/a").text)

                    for page in range(2, number_of_pages + 2):
                        if page > 2:
                            WebDriverWait(self.driver, 5).until(EC.presence_of_element_located((By.XPATH, "//*[@id='main-body']/div[4]/div/div[3]/div/div[4]/div[2]")))

                        ad_links = self.driver.find_elements_by_xpath("//article")
                        for i in range(1, len(ad_links) + 1):
                            if i != 1:
                                WebDriverWait(self.driver, 5).until(EC.presence_of_element_located((By.XPATH, "//*[@id='main-body']/div[4]/div/div[3]/div/div[4]/div[2]")))

                            link = self.driver.find_element_by_xpath(f"//article[{i}]")

                            link.location_once_scrolled_into_view # returns dict of X, Y coordinates
                            # self.driver.execute_script('window.scrollTo({}, {});'.format(coordinates['x'], coordinates['y']))
                            # WebDriverWait(self.driver, 5).until(EC.staleness_of(link))

                            try:
                                link.click()
                                img_elements = self.driver.find_elements_by_xpath("//div[contains(@class, 'detail-gallery')]//img")
                                label = f"{str.lower(brand)} {str.lower(model)}"
                                data_path = os.path.join(self.download_path, label)

                                if not os.path.exists(data_path):
                                    os.makedirs(data_path)

                                for img_element in img_elements:
                                    src = img_element.get_attribute("src")
                                    parsed_url = urlparse(src).path
                                    filename = os.path.basename(parsed_url)
                                    download_path = os.path.join(data_path, filename)
                                    if os.path.exists(f"/media/alp/Yeni Birim/scraped_cars/{filename}"):
                                        print(f"Exists in training data: {filename}")
                                        continue

                                    try:
                                        urlretrieve(src, download_path)
                                        self.total_downloads[brand][model] += 1
                                        sys.stdout.write("\r" + f"{self.total_downloads[brand][model]}")
                                        sys.stdout.flush()
                                    except:
                                        continue
                                self.driver.execute_script("window.history.go(-1)")
                            except Exception as ex:
                                print(ex)

                            if self.total_downloads[brand][model] >= self.download_limit:
                                break
                        if self.total_downloads[brand][model] >= self.download_limit:
                            break
                        link_to_follow = self.driver.find_element_by_xpath(f"//span[@class='numerotation']/a[contains(text(), {page})]")
                        link_to_follow.click()
                    if self.total_downloads[brand][model] >= self.download_limit:
                        break


if __name__ == '__main__':
    models = {
        # "Volvo": ["V40", "V50"],
        "Opel": ["Astra"],
        "Renault": ["Clio", "Megane", "Captur"],
        "Audi": ["A3", "A4", "Q7"],
        "BMW": ["1 Series"],
        "Volkswagen": ["Passat", "Polo", "Golf"]

    }

    scraper = SeleniumScraper("/home/alp/Desktop/chromedriver",
                            download_path="/media/alp/Yeni Birim/car_dataset_test/",
                            urls=["https://gocar.be/en/second-hand-car-used"],
                            models=models)
    scraper.scrape()
